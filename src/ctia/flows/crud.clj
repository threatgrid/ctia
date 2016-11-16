(ns ctia.flows.crud
  "This namespace handle all necessary flows for creating, updating
  and deleting entities."
  (:import java.util.UUID)
  (:require [clojure.tools.logging :as log]
            [ctia.auth :as auth]
            [ctia.flows.hooks :as h]
            [ctia.properties :refer [properties]]
            [ctia.store :as store]
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
   (s/optional-key :long-id-fn) (s/maybe (s/pred fn?))
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

(defn- find-checked-id
  "Like find-id above, but checks that the hostname in the ID (if it
  is a long ID) is the local server hostname.  Throws bad-request! on
  mismatch."
  [{id :id, :as entity}]
  (when (seq id)
    (if (id/long-id? id)
      (let [id-rec (id/long-id->id id)
            this-host (get-in @properties [:ctia :http :show :hostname])]
        (if (= (:hostname id-rec) this-host)
          (:short-id id-rec)
          (throw (http-response/bad-request!
                  {:error "Invalid hostname in ID"
                   :id id
                   :this-host this-host
                   :entity entity}))))
      (id/str->short-id id))))

(defn- make-id
  [entity-type]
  (str (name entity-type) "-" (UUID/randomUUID)))

(s/defn ^:private find-entity-id :- s/Str
  [{:keys [identity entity-type prev-entity]} :- FlowMap
   entity :- {s/Keyword s/Any}]
  (or (find-id prev-entity)
      (when-let [entity-id (find-checked-id entity)]
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
  (doseq [entity entities]
    (h/apply-hooks :entity entity
                   :prev-entity prev-entity
                   :hook-type (case flow-type
                                :create :after-create
                                :update :after-update
                                :delete :after-delete)
                   :read-only? true))
  fm)

(s/defn ^:private create-events :- FlowMap
  [{:keys [create-event-fn entities flow-type identity prev-entity]
    :as fm} :- FlowMap]
  (if (get-in @properties [:ctia :events :enabled])
    (assoc fm
           :events
           (doall
            (for [entity entities]
              (try
                (if (= :update flow-type)
                  (create-event-fn entity prev-entity (make-id "event"))
                  (create-event-fn entity (make-id "event")))
                (catch Throwable e
                  (log/error "Could not create event" e)
                  (throw (ex-info "Could not create event"
                                  {:flow-type flow-type
                                   :login (auth/login identity)
                                   :entity entity
                                   :prev-entity prev-entity})))))))
    fm))

(s/defn ^:private write-events :- FlowMap
  [{:keys [events] :as fm} :- FlowMap]
  (if events
    (assoc fm
           :events
           (store/write-store :event store/create-events events))
    fm))

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

(s/defn ^:private apply-long-id-fn :- FlowMap
  [{:keys [entities long-id-fn] :as fm}]
  (if long-id-fn
    (assoc fm
           :entities
           (doall
            (for [entity entities]
              (long-id-fn entity))))
    fm))

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
             entities
             long-id-fn]}]
  (-> {:flow-type :create
       :entity-type entity-type
       :entities entities
       :identity identity
       :long-id-fn long-id-fn
       :realize-fn realize-fn
       :store-fn store-fn
       :create-event-fn to-create-event}
      realize-entities
      apply-before-hooks
      apply-store-fn
      apply-long-id-fn
      create-events
      write-events
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
             entity
             long-id-fn]}]
  (let [prev-entity (get-fn entity-id)]
    (-> {:flow-type :update
         :entity-type entity-type
         :entities [entity]
         :prev-entity prev-entity
         :identity identity
         :long-id-fn long-id-fn
         :realize-fn realize-fn
         :store-fn update-fn
         :create-event-fn to-update-event}
        realize-entities
        apply-before-hooks
        apply-store-fn
        apply-long-id-fn
        create-events
        write-events
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
        apply-store-fn
        create-events
        write-events
        apply-event-hooks
        apply-after-hooks
        make-result)))
