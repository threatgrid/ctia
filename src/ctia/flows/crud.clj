(ns ctia.flows.crud
  "This namespace handle all necessary flows for creating, updating
  and deleting entities."
  (:import java.util.UUID)
  (:require [clojure.tools.logging :as log]
            [ctia.auth :as auth]
            [ctia.flows.hooks :as h]
            [ctim.events.obj-to-event :refer [to-create-event
                                              to-update-event
                                              to-delete-event]]
            [ctim.domain.id :as id]
            [ring.util.http-response :as http-response]
            [schema.core :as s]))

(s/defschema FlowMap
  {:create-event-fn (s/pred fn?)
   :entities [{s/Keyword s/Any}]
   :entity-type s/Keyword
   (s/optional-key :events) [{s/Keyword s/Any}]
   :flow-type (s/enum :create :update :delete)
   :identity (s/protocol auth/IIdentity)
   (s/optional-key :prev-entity) (s/maybe {s/Keyword s/Any})
   (s/optional-key :realize-fn) (s/pred fn?)
   (s/optional-key :results) [s/Bool]
   :store-fn (s/pred fn?)})

(defn- find-id
  "Lookup an ID in a given entity.  Parse it, because it might be a
   URL, and return the short form ID.  Returns nil if the ID could not
   be found."
  [{id :id}]
  (when (seq id)
    (id/str->short-id id)))

(defn- make-id
  [entity-type]
  (str (name entity-type) "-" (UUID/randomUUID)))

(s/defn ^:private find-entity-id :- s/Str
  [{:keys [identity entity-type prev-entity]} :- FlowMap
   entity :- {s/Keyword s/Any}]
  (or (find-id prev-entity)
      (when-let [entity-id (find-id entity)]
        (when-not (auth/capable? identity :specify-id)
          (throw (http-response/bad-request!
                  {:error "Missing capability to specify entity ID"
                   :entity entity})))
        (if (id/valid-short-id? entity-id)
          entity-id
          (throw (http-response/bad-request!
                  {:error (format "Invalid entity ID: %s" entity-id)
                   :entity entity}))))
      (make-id entity-type)))

(s/defn ^:private realize-entities :- FlowMap
  [{:keys [entities flow-type identity prev-entity realize-fn] :as fm} :- FlowMap]
  (let [login (auth/login identity)]
    (assoc fm
           :entities
           (doall
            (for [entity entities
                  :let [entity-id (find-entity-id fm entity)]]
              (case flow-type
                :create (realize-fn entity entity-id login)
                :update (realize-fn entity entity-id login prev-entity)
                :delete entity))))))

(s/defn ^:private apply-before-hooks :- FlowMap
  [{:keys [entities flow-type prev-entity] :as fm} :- FlowMap]
  (assoc fm
         :entities
         (doall
          (for [entity entities]
            (h/apply-hooks :entity entity
                           :prev-entity prev-entity
                           :hook-type (case flow-type
                                        :create :before-create
                                        :update :before-update
                                        :delete :before-delete)
                           :read-only? (= flow-type :delete))))))

(s/defn ^:privae apply-after-hooks :- FlowMap
  [{:keys [entities flow-type prev-entity] :as fm} :- FlowMap]
  (doall
   (for [entity entities]
     (h/apply-hooks :entity entity
                    :prev-entity prev-entity
                    :hook-type (case flow-type
                                 :create :after-create
                                 :update :after-update
                                 :delete :after-delete)
                    :read-only? true)))
  fm)

(s/defn ^:private create-events :- FlowMap
  [{:keys [create-event-fn entities flow-type identity prev-entity]
    :as fm} :- FlowMap]
  (assoc fm
         :events
         (doall
          (for [entity entities]
            (try
              (if (= :update flow-type)
                (create-event-fn entity prev-entity)
                (create-event-fn entity))
              (catch Throwable e
                (log/error "Could not create event" e)
                (throw (ex-info "Could not create event"
                                {:flow-type flow-type
                                 :login (auth/login identity)
                                 :entity entity
                                 :prev-entity prev-entity}))))))))

(s/defn ^:private apply-store-fn :- FlowMap
  [{:keys [entities flow-type store-fn] :as fm} :- FlowMap]
  (case flow-type
    :create
    (assoc fm
           :entities
           (store-fn entities))

    :delete
    (assoc fm
           :results
           (doall
            (for [{entity-id :id} entities]
              (store-fn entity-id))))

    :update
    (assoc fm
           :entities
           (doall
            (for [entity entities]
              (store-fn entity))))))

(s/defn ^:private apply-event-hooks :- FlowMap
  [{:keys [events] :as fm} :- FlowMap]
  (doseq [event events]
    (h/apply-event-hooks event))
  fm)

(s/defn ^:private make-result :- s/Any
  [{:keys [flow-type] :as fm} :- FlowMap]
  (case flow-type
    :create (:entities fm)
    :delete (first (:results fm))
    :update (first (:entities fm))))

(defn create-flow
  "This function centralizes the create workflow.
  It is helpful to easily add new hooks name

  To be noted:
    - `:before-create` hooks can modify the entity stored.
    - `:after-create` hooks are read only"
  [& {:keys [entity-type
             realize-fn
             store-fn
             identity
             entities]}]
  (-> {:flow-type :create
       :entity-type entity-type
       :entities entities
       :identity identity
       :realize-fn realize-fn
       :store-fn store-fn
       :create-event-fn to-create-event}
      realize-entities
      apply-before-hooks
      create-events
      apply-store-fn
      apply-event-hooks
      apply-after-hooks
      make-result))

(defn update-flow
  "This function centralize the update workflow.
  It is helpful to easily add new hooks name

  To be noted:
    - `:before-update` hooks can modify the entity stored.
    - `:after-update` hooks are read only"
  [& {:keys [entity-type
             get-fn
             realize-fn
             update-fn
             entity-id
             identity
             entity]}]
  (let [prev-entity (get-fn entity-id)]
    (-> {:flow-type :update
         :entity-type entity-type
         :entities [entity]
         :prev-entity prev-entity
         :identity identity
         :realize-fn realize-fn
         :store-fn update-fn
         :create-event-fn to-update-event}
        realize-entities
        apply-before-hooks
        create-events
        apply-store-fn
        apply-event-hooks
        apply-after-hooks
        make-result)))

(defn delete-flow
  "This function centralize the deletion workflow.
  It is helpful to easily add new hooks name

  To be noted:
    - the flow get the entity from the store to be used by hooks.
    - `:before-delete` hooks can modify the entity stored.
    - `:after-delete` hooks are read only"
  [& {:keys [entity-type
             get-fn
             delete-fn
             entity-id
             identity]}]
  (let [entity (get-fn entity-id)]
    (-> {:flow-type :delete
         :entity-type entity-type
         :entities [entity]
         :prev-entity entity
         :identity identity
         :store-fn delete-fn
         :create-event-fn to-delete-event}
        apply-before-hooks
        create-events
        apply-store-fn
        apply-event-hooks
        apply-after-hooks
        make-result)))
