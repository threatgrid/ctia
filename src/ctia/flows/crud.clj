(ns ctia.flows.crud
  "This namespace handle all necessary flows for creating, updating
  and deleting entities."
  (:require [clj-momo.lib.map :refer [deep-merge-with]]
            [clojure.spec.alpha :as cs]
            [clojure.tools.logging :as log]
            [ctia
             [auth :as auth]
             [properties :refer [properties]]
             [store :as store]]
            [ctia.domain
             [access-control :refer [allowed-tlp? allowed-tlps]]
             [entities :refer [un-store]]]
            [ctia.flows.hooks :as h]
            [ctia.schemas.core :refer [TempIDs]]
            [ctim.domain.id :as id]
            [ctim.events.obj-to-event
             :refer
             [to-create-event
              to-delete-event
              to-update-event]]
            [ring.util.http-response :as http-response]
            [schema.core :as s])
  (:import java.util.UUID))

(s/defschema FlowMap
  {:create-event-fn (s/pred fn?)
   :entities [{s/Keyword s/Any}]
   :entity-type s/Keyword
   (s/optional-key :events) [{s/Keyword s/Any}]
   :flow-type (s/enum :create :update :delete)
   :identity (s/protocol auth/IIdentity)
   (s/optional-key :long-id-fn) (s/maybe (s/pred fn?))
   (s/optional-key :prev-entity) (s/maybe {s/Keyword s/Any})
   (s/optional-key :partial-entity) (s/maybe {s/Keyword s/Any})
   (s/optional-key :patch-operation) (s/enum :add :remove :replace)
   (s/optional-key :realize-fn) (s/pred fn?)
   (s/optional-key :results) [s/Bool]
   (s/optional-key :spec) (s/maybe s/Keyword)
   (s/optional-key :tempids) (s/maybe TempIDs)
   (s/optional-key :enveloped-result?) (s/maybe s/Bool)
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

(defn make-id
  [entity-type]
  (str (name entity-type) "-" (UUID/randomUUID)))

(s/defn ^:private find-entity-id :- s/Str
  [{:keys [identity entity-type prev-entity tempids]} :- FlowMap
   entity :- {s/Keyword s/Any}]
  (or (find-id prev-entity)
      (get tempids (:id entity))
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

(s/defn ^:private validate-entities :- FlowMap
  [{:keys [spec entities] :as fm} :- FlowMap]
  (when spec
    (doseq [entity entities]
      (when-not (cs/valid? spec entity)
        (throw (http-response/bad-request!
                {:error (cs/explain-str spec entity)
                 :entity entity})))
      (when-let [entity-tlp (:tlp entity)]
        (when-not (allowed-tlp? entity-tlp)
          (throw (http-response/bad-request!
                  {:error (format "Invalid document TLP %s, allowed TLPs are: %s"
                                  entity-tlp
                                  (clojure.string/join "," (allowed-tlps)))
                   :entity entity}))))))
  fm)

(s/defn ^:private create-ids-from-transient :- FlowMap
  "Creates IDs for entities identified by transient IDs that have not
   yet been resolved."
  [{:keys [entities
           entity-type]
    :as fm} :- FlowMap]
  (let [newtempids
        (->> entities
             (keep (fn [{:keys [id]}]
                     (when (and id (re-matches id/transient-id-re id))
                       [id (make-id entity-type)])))
             (into {}))]
    (update fm :tempids (fnil into {}) newtempids)))

(s/defn ^:private realize-entities :- FlowMap
  [{:keys [entities
           flow-type
           identity
           tempids
           prev-entity
           realize-fn] :as fm} :- FlowMap]
  (let [login (auth/login identity)
        groups (auth/groups identity)]
    (assoc fm
           :entities
           (doall
            (for [entity entities
                  :let [entity-id (find-entity-id fm entity)]]
              (case flow-type
                :create (realize-fn entity
                                    entity-id
                                    tempids
                                    login
                                    groups)
                :update (if prev-entity
                          (realize-fn entity
                                      entity-id
                                      tempids
                                      login
                                      groups
                                      prev-entity)
                          (realize-fn entity
                                      entity-id
                                      tempids
                                      login
                                      groups))
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

(s/defn ^:private apply-after-hooks :- FlowMap
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
    (let [events
          (->> entities
               (filter #(nil? (:error %)))
               (map (fn [entity]
                      (try
                        (if (= :update flow-type)
                          (create-event-fn entity prev-entity
                                           (make-id "event"))
                          (create-event-fn entity (make-id "event")))
                        (catch Throwable e
                          (log/error "Could not create event" e)
                          (throw (ex-info "Could not create event"
                                          {:flow-type flow-type
                                           :login (auth/login identity)
                                           :entity entity
                                           :prev-entity prev-entity}))))))
               doall)]
      (cond-> fm
        (seq events) (assoc :events events)))
    fm))

(s/defn ^:private write-events :- FlowMap
  [{:keys [events] :as fm} :- FlowMap]
  (if (seq events)
    (assoc fm
           :events
           (store/write-store :event store/create-events events))
    fm))

(s/defn apply-create-store-fn
  [{:keys [entities store-fn enveloped-result? tempids] :as fm} :- FlowMap]
  (try
    (assoc fm
           :entities
           (store-fn entities))
    (catch Exception e
      (if enveloped-result?
        ;; Set partial results with errors if the enveloped-results format
        ;; is used, otherwise throw an exception
        (if-let [{:keys [data]} (ex-data e)]
          (assoc fm :entities data)
          (throw e))
        (throw e)))))

(s/defn ^:private apply-store-fn :- FlowMap
  [{:keys [entities flow-type store-fn] :as fm} :- FlowMap]
  (case flow-type
    :create (apply-create-store-fn fm)

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

(defn short-to-long-ids-map
  "Builds a mapping table between short and long IDs"
  [entities long-id-fn]
  (->> entities
       (filter #(nil? (:error %)))
       (map (fn [{:keys [error id] :as entity}]
              [id (:id (long-id-fn entity))]))
       (into {})))

(defn entities-with-long-ids
  "Converts entity IDs from short to long format"
  [entities short-to-long-map]
  (map (fn [{:keys [id] :as entity}]
         (cond-> entity
           id (assoc :id (get short-to-long-map id))))
       entities))

(defn tempids-with-long-ids
  "Converts IDs in the tempids mapping table from short to long format"
  [tempids short-to-long-map]
  (->> tempids
       (keep (fn [[tempid short-id]]
               (when-let [long-id (get short-to-long-map short-id)]
                 [tempid long-id])))
       (into {})))

(s/defn ^:private apply-long-id-fn :- FlowMap
  [{:keys [entities tempids long-id-fn] :as fm}]
  (if long-id-fn
    (let [short-to-long-map (short-to-long-ids-map entities long-id-fn)]
      (assoc fm
             :entities (entities-with-long-ids entities short-to-long-map)
             :tempids (tempids-with-long-ids tempids short-to-long-map)))
    fm))

(s/defn ^:private apply-event-hooks :- FlowMap
  [{:keys [events] :as fm} :- FlowMap]
  (doseq [event events]
    (h/apply-event-hooks event))
  fm)

(s/defn ^:private make-result :- s/Any
  [{:keys [flow-type entities results
           enveloped-result? tempids] :as fm} :- FlowMap]
  (case flow-type
    :create (if enveloped-result?
              (cond-> {:data entities}
                (seq tempids) (assoc :tempids tempids))
              entities)
    :delete (first results)
    :update (first entities)))

(defn recast
  "given a source collection and the target one,
   cast target the same as source"
  [orig-coll new-coll]
  (cond
    (vector? orig-coll) (vec new-coll)
    (set? orig-coll) (set new-coll)
    :else new-coll))

(defn add-colls [& args]
  "given many collections as argument
   concat them keeping the first argument type"
  (let [new-coll
        (->> args
             (map #(or % []))
             (reduce into))]
    (recast (first args) new-coll)))

(defn remove-colls
  "given many collections as argument
   remove items on a from b successively"
  [& args]
  (let [new-coll
        (reduce
         (fn [a b]
           (remove (or (set b) #{})
                   (or a []))) args)]
    (recast (first args) new-coll)))

(defn replace-colls
  "given many collections as argument
   replace a from b successively"
  [& args]
  (let [new-coll (last args)]
    (recast (first args) new-coll)))

(s/defn patch-entities :- FlowMap
  [{:keys [prev-entity
           partial-entity
           patch-operation]
    :as fm} :- FlowMap]

  (let [patch-fn (case patch-operation
                   :add add-colls
                   :remove remove-colls
                   :replace replace-colls
                   replace-colls)
        entity (-> (deep-merge-with patch-fn
                                    prev-entity
                                    partial-entity)
                   un-store
                   (dissoc :id))]
    (assoc fm :entities [entity])))

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
             tempids
             long-id-fn
             spec
             enveloped-result?]}]
  (-> {:flow-type :create
       :entity-type entity-type
       :entities entities
       :tempids tempids
       :identity identity
       :long-id-fn long-id-fn
       :realize-fn realize-fn
       :spec spec
       :store-fn store-fn
       :create-event-fn to-create-event
       :enveloped-result? enveloped-result?}
      validate-entities
      create-ids-from-transient
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
             long-id-fn
             spec]}]
  (let [prev-entity (get-fn entity-id)]
    (-> {:flow-type :update
         :entity-type entity-type
         :entities [entity]
         :prev-entity prev-entity
         :identity identity
         :long-id-fn long-id-fn
         :realize-fn realize-fn
         :spec spec
         :store-fn update-fn
         :create-event-fn to-update-event}
        validate-entities
        realize-entities
        apply-before-hooks
        apply-store-fn
        apply-long-id-fn
        create-events
        write-events
        apply-event-hooks
        apply-after-hooks
        make-result)))

(defn patch-flow
  "This function centralizes the patch workflow.
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
             patch-operation
             partial-entity
             long-id-fn
             spec]}]
  (let [prev-entity (get-fn entity-id)]
    (-> {:flow-type :update
         :entity-type entity-type
         :entities []
         :prev-entity prev-entity
         :partial-entity partial-entity
         :patch-operation patch-operation
         :identity identity
         :long-id-fn long-id-fn
         :realize-fn realize-fn
         :spec spec
         :store-fn update-fn
         :create-event-fn to-update-event}
        patch-entities
        validate-entities
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
