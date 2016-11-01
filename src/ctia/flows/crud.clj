(ns ctia.flows.crud
  "This namespace handle all necessary flows for creating, updating and deleting entities."
  (:import java.util.UUID)
  (:require [clojure.core.async :as a]
            [clojure.core.async.impl.protocols :as ap]
            [clojure.tools.logging :as log]
            [ctia.auth :as auth]
            [ctia.flows.hooks :as h]
            [ctia.lib.async :as la]
            [ctim.events.obj-to-event :refer [to-create-event
                                              to-update-event
                                              to-delete-event]]
            [ctim.domain.id :as id]
            [ring.util.http-response :as http-response]
            [schema.core :as s]))

(def FlowMap
  "Parameters that are common across multiple fns below"
  {:create-event-fn (s/pred fn?)
   (s/optional-key :delete-chan) (s/protocol ap/Channel)
   :entity {s/Keyword s/Any}
   (s/optional-key :entity-chan) (s/protocol ap/Channel)
   (s/optional-key :entity-id) s/Str
   :entity-type s/Keyword
   (s/optional-key :event-chan) (s/protocol ap/Channel)
   :flow-type (s/enum :create :update :delete)
   :identity (s/protocol auth/IIdentity)
   :login s/Str
   (s/optional-key :prev-entity) (s/maybe {s/Keyword s/Any})
   (s/optional-key :realize-fn) (s/pred fn?)
   (s/optional-key :result-chan) (s/protocol ap/Channel)
   (s/optional-key :side-effect-chans) [(s/protocol ap/Channel)]
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

(s/defn ^:private find-entity-id :- FlowMap
  [{:keys [entity entity-id entity-type identity prev-entity] :as fm} :- FlowMap]
  (assoc fm
         :entity-id
         (or entity-id
             (find-id prev-entity)
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
             (make-id entity-type))))

(s/defn ^:private realize-entity :- FlowMap
  [{:keys [realize-fn flow-type entity prev-entity login entity-id] :as fm} :- FlowMap]
  (assoc fm
         :entity-chan
         (la/on-chan
          (case flow-type
            :create (realize-fn entity entity-id login)
            :update (realize-fn entity entity-id login prev-entity)
            :delete entity))))

(defn- apply-create-event-fn
  [create-event-fn realized prev-entity flow-type login]
  (try
    (if (= :update flow-type)
      (create-event-fn realized prev-entity)
      (create-event-fn realized))
    (catch Throwable e
      (log/error "Could not create event" e)
      (throw (ex-info "Could not create event"
                      {:flow-type flow-type
                       :login login
                       :entity realized
                       :prev-entity prev-entity})))))

(s/defn ^:private create-event-xf :- (s/pred fn?)
  [{:keys [create-event-fn prev-entity flow-type login]} :- FlowMap]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result entity]
       (let [event (apply-create-event-fn create-event-fn
                                          entity
                                          prev-entity
                                          flow-type
                                          login)]
         (rf result event))))))

(s/defn ^:private make-event-chan :- FlowMap
  "Take an entity-chan and, using a mult, clone it to a new
  entity-chan and an event-chan.  The event chan uses an event
  producing transducer."
  [{:keys [entity-chan] :as fm} :- FlowMap]

  (let [m-chan (a/chan)
        mult (a/mult m-chan)
        new-entity-chan (a/chan)
        new-event-chan (a/chan 1 ; must be >0
                               (create-event-xf fm)
                               la/throwable)]
    (a/tap mult new-event-chan)
    (a/tap mult new-entity-chan)
    ;; Have to pipe after taps to avoid race
    (a/pipe entity-chan m-chan)
    (assoc fm
           :entity-chan new-entity-chan
           :event-chan new-event-chan)))

(s/defn ^:private apply-before-hooks :- FlowMap
  [{:keys [prev-entity entity-chan flow-type] :as fm} :- FlowMap]
  (assoc fm
         :entity-chan
         (h/apply-hooks :entity-chan entity-chan
                        :prev-entity prev-entity
                        :hook-type (case flow-type
                                     :create :before-create
                                     :update :before-update
                                     :delete :before-delete)
                        :read-only? (= flow-type :delete))))

(s/defn ^:private apply-event-hooks :- FlowMap
  [{:keys [event-chan] :as fm} :- FlowMap]
  (assoc fm
         :event-chan
         (h/apply-event-hooks event-chan)))

(s/defn ^:private apply-after-hooks :- FlowMap
  [{:keys [prev-entity entity-chan flow-type] :as fm} :- FlowMap]
  (assoc fm
         :entity-chan
         (h/apply-hooks :entity-chan entity-chan
                        :prev-entity prev-entity
                        :hook-type (case flow-type
                                     :create :after-create
                                     :update :after-update
                                     :delete :after-delete)
                        :read-only? true)))

(s/defn ^:private apply-store-fn :- FlowMap
  [{:keys [entity-chan store-fn] :as fm} :- FlowMap]
  (assoc fm
         :entity-chan
         (store-fn entity-chan)))

(s/defn ^:private apply-delete-fn :- FlowMap
  [{:keys [entity-id store-fn] :as fm} :- FlowMap]
  (assoc fm
         :delete-chan
         (store-fn (doto (a/promise-chan)
                     (a/>!! entity-id)))))

(s/defn ^:private result-chan :- FlowMap
  [fm :- FlowMap
   fm-key :- s/Keyword]
  (-> fm
      (assoc :result-chan (get fm fm-key))
      (dissoc fm-key)))

(s/defn ^:private side-effects-chan :- FlowMap
  [fm :- FlowMap
   fm-key :- s/Keyword]
  (-> fm
      (update :side-effect-chans conj (get fm fm-key))
      (dissoc fm-key)))

(s/defn pop-result :- (s/conditional map? {s/Keyword s/Any}
                                     :else s/Bool)
  "Takes a FlowMap and waits on all of the side-effect-chans
   and the result-chan, returning the latter's output."
  [{:keys [result-chan side-effect-chans]} :- FlowMap]
  (doseq [c side-effect-chans]
    (la/<!! c))
  (la/<!! result-chan))

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
             entity]}]
  (-> {:create-event-fn to-create-event
       :flow-type :create
       :entity entity
       :entity-type entity-type
       :identity identity
       :login (auth/login identity)
       :realize-fn realize-fn
       :store-fn store-fn}
      find-entity-id
      realize-entity
      apply-before-hooks
      make-event-chan
      apply-store-fn
      apply-event-hooks
      (side-effects-chan :event-chan)
      apply-after-hooks
      (result-chan :entity-chan)))

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
  (-> {:create-event-fn to-update-event
       :flow-type :update
       :entity entity
       :entity-type entity-type
       :identity identity
       :login (auth/login identity)
       :prev-entity (get-fn entity-id)
       :realize-fn realize-fn
       :store-fn update-fn}
      find-entity-id
      realize-entity
      apply-before-hooks
      make-event-chan
      apply-store-fn
      apply-event-hooks
      (side-effects-chan :event-chan)
      apply-after-hooks
      (result-chan :entity-chan)))

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
    (-> {:create-event-fn to-delete-event
         :entity entity
         :entity-chan (la/on-chan entity)
         :entity-type entity-type
         :flow-type :delete
         :identity identity
         :login (auth/login identity)
         :prev-entity entity
         :store-fn delete-fn}
        find-entity-id
        apply-before-hooks
        make-event-chan
        apply-delete-fn
        apply-event-hooks
        (side-effects-chan :event-chan)
        apply-after-hooks
        (side-effects-chan :entity-chan)
        (result-chan :delete-chan))))
