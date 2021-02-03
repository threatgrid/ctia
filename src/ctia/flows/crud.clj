(ns ctia.flows.crud
  "This namespace handle all necessary flows for creating, updating
  and deleting entities."
  (:require [clj-momo.lib.map :refer [deep-merge-with]]
            [clojure.string :as str]
            [clojure.set :refer [index]]
            [clojure.spec.alpha :as cs]
            [clojure.tools.logging :as log]
            [ctia.entity.event.schemas :refer [Event]]
            [ctia
             [auth :as auth]
             [store :as store]]
            [ctia.domain
             [access-control :refer [allowed-tlp? allowed-tlps]]
             [entities :refer [un-store]]]
            [ctia.properties :as p]
            [ctia.schemas.core :refer [APIHandlerServices
                                       APIHandlerServices->RealizeFnServices
                                       HTTPShowServices
                                       RealizeFn
                                       lift-realize-fn-with-context
                                       TempIDs]]
            [ctim.domain.id :as id]
            [ctia.lib.collection :as coll]
            [ctia.entity.event.obj-to-event
             :refer
             [to-create-event
              to-delete-event
              to-update-event]]
            [ring.util.http-response :as http-response]
            [schema.core :as s])
  (:import java.util.UUID))

(s/defschema FlowMap
  {:create-event-fn                    (s/pred fn?)
   :entities                           [{s/Keyword s/Any}]
   :entity-type                        s/Keyword
   (s/optional-key :events)            [{s/Keyword s/Any}]
   :flow-type                          (s/enum :create :update :delete)
   :services                           APIHandlerServices
   :identity                           (s/protocol auth/IIdentity)
   (s/optional-key :long-id-fn)        (s/maybe (s/=> s/Any s/Any))
   (s/optional-key :prev-entity)       (s/maybe {s/Keyword s/Any})
   (s/optional-key :partial-entity)    (s/maybe {s/Keyword s/Any})
   (s/optional-key :patch-operation)   (s/enum :add :remove :replace)
   (s/optional-key :realize-fn)        RealizeFn
   (s/optional-key :results)           [s/Bool]
   (s/optional-key :spec)              (s/maybe s/Keyword)
   (s/optional-key :tempids)           (s/maybe TempIDs)
   (s/optional-key :enveloped-result?) (s/maybe s/Bool)
   :store-fn                           (s/=> s/Any s/Any)})

(defn- find-id
  "Lookup an ID in a given entity.  Parse it, because it might be a
   URL, and return the short form ID.  Returns nil if the ID could not
   be found."
  [{id :id}]
  (when (seq id)
    (id/str->short-id id)))

(s/defn ^:private find-checked-id
  "Like find-id above, but checks that the hostname in the ID (if it
  is a long ID) is the local server hostname.  Throws bad-request! on
  mismatch."
  [{id :id, :as entity}
   services :- HTTPShowServices]
  (when (seq id)
    (if (id/long-id? id)
      (let [id-rec (id/long-id->id id)
            this-host (:hostname (p/get-http-show services))]
        (if (= (:hostname id-rec) this-host)
          (:short-id id-rec)
          (http-response/bad-request!
           {:error "Invalid hostname in ID"
            :id id
            :this-host this-host
            :entity entity})))
      (id/str->short-id id))))

(defn gen-random-uuid []
  (UUID/randomUUID))

(defn make-id
  [entity-type]
  (str (name entity-type) "-" (gen-random-uuid)))

(s/defn ^:private find-entity-id :- s/Str
  [{identity-obj :identity
    :keys [entity-type prev-entity tempids]} :- FlowMap
   entity :- {s/Keyword s/Any}
   services :- HTTPShowServices]
  (or (find-id prev-entity)
      (get tempids (:id entity))
      (when-let [entity-id (find-checked-id entity services)]
        (when-not (auth/capable? identity-obj :specify-id)
          (http-response/forbidden!
           {:error "Missing capability to specify entity ID"
            :entity entity}))
        (if (id/valid-short-id? entity-id)
          entity-id
          {:error (format "Invalid entity ID: %s" entity-id)
           :entity entity}))
      (make-id entity-type)))

(defn- check-spec [entity spec]
  (if (and spec
           (not (cs/valid? spec entity)))
    {:msg (cs/explain-str spec entity)
     :error "Entity validation Error"
     :type :spec-validation-error
     :entity entity}
    entity))

(defn tlp-check
  [{:keys [tlp] :as entity} get-in-config]
  (cond
    (not (seq tlp)) entity
    (not (allowed-tlp? tlp get-in-config))
    {:msg (format "Invalid document TLP %s, allowed TLPs are: %s"
                  tlp
                  (str/join "," (allowed-tlps get-in-config)))
     :error "Entity Access Control validation Error"
     :type :invalid-tlp-error
     :entity entity}
    :else entity))

(s/defn ^:private validate-entities :- FlowMap
  [{{{:keys [get-in-config]} :ConfigService
     :as services} :services
    :keys [spec entities] :as fm} :- FlowMap]
  (assoc fm :entities
         (map (fn [entity]
                (-> entity
                     (check-spec spec)
                     (tlp-check get-in-config))) entities)))

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
           services
           tempids
           prev-entity] :as fm} :- FlowMap]
  (let [login (auth/login identity)
        groups (auth/groups identity)
        realize-fn (lift-realize-fn-with-context
                     (:realize-fn fm)
                     {:services (APIHandlerServices->RealizeFnServices
                                  services)})]
    (assoc fm
           :entities
           (doall
            (for [entity entities
                  :let [entity-id (find-entity-id fm entity services)]]
              (cond
                (:error entity) entity
                (:error entity-id) entity-id
                :else (case flow-type
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
                        :delete entity)))))))

(s/defn ^:private throw-validation-error
  [{:keys [entities enveloped-result?] :as fm} :- FlowMap]
  (let [errors (filter :error entities)]
    (if (and (seq errors)
             (not enveloped-result?))
      (let [{:keys [msg error] :as full-error} (first errors)]
        (throw
         (ex-info (or msg error)
                  ;; short-id is only used for the enveloped-result
                  ;; it should not be exposed
                  (dissoc full-error :id))))
      fm)))

(s/defn ^:private apply-before-hooks :- FlowMap
  [{{{:keys [apply-hooks]} :HooksService} :services
    :keys [entities flow-type prev-entity] :as fm} :- FlowMap]
  (assoc fm
         :entities
         (doall
          (for [entity entities]
            (apply-hooks {:entity entity
                          :prev-entity prev-entity
                          :hook-type (case flow-type
                                       :create :before-create
                                       :update :before-update
                                       :delete :before-delete)
                          :read-only? (= flow-type :delete)})))))

(s/defn ^:private apply-after-hooks :- FlowMap
  [{{{:keys [apply-hooks]} :HooksService} :services
    :keys [entities flow-type prev-entity] :as fm} :- FlowMap]
  (doseq [entity entities]
    (apply-hooks {:entity entity
                  :prev-entity prev-entity
                  :hook-type (case flow-type
                               :create :after-create
                               :update :after-update
                               :delete :after-delete)
                  :read-only? true}))
  fm)

(s/defn ^:private create-events :- FlowMap
  [{:keys [create-event-fn flow-type identity prev-entity entities]
    {{:keys [get-in-config]} :ConfigService}
    :services
    :as fm} :- FlowMap]
  (if (get-in-config [:ctia :events :enabled])
    (let [login (auth/login identity)
          create-event (fn [entity]
                         (let [event-id (make-id "event")]
                           (try
                             (if (= flow-type :update)
                               (create-event-fn entity prev-entity event-id login)
                               (create-event-fn entity event-id login))
                             (catch Throwable e
                               (log/error "Could not create event" e)
                               (throw (ex-info "Could not create event"
                                               {:flow-type flow-type
                                                :login login
                                                :entity entity
                                                :prev-entity prev-entity}))))))
          events (->> entities
                      (filter #(nil? (:error %)))
                      (map create-event)
                      doall)]
      (cond-> fm
        (seq events) (assoc :events events)))
    fm))

(s/defn ^:private write-events :- FlowMap
  [{{{:keys [get-store]} :StoreService} :services
    :keys [events] :as fm} :- FlowMap]
  (if (seq events)
    (assoc fm
           :events
           (-> (get-store :event)
               (store/create-events events)))
    fm))

(s/defn remove-errors :- FlowMap
  "Remove all entities with an error from the given FlowMap"
  [fm :- FlowMap]
  (update fm :entities #(filter (complement :error) %)))

(s/defn preserve-errors :- FlowMap
  "Preserve errors and the order of entities after applying
   f to the provided flow map"
  [{:keys [entities enveloped-result?] :as fm} :- FlowMap
   f]
  (if enveloped-result?
    (let [{result-entities :entities :as result} (f (remove-errors fm))
          entities-by-id (index result-entities [:id])
          result-entity-fn (fn [id] (first (get entities-by-id {:id id})))
          new-entities (keep (fn [{:keys [id] :as entity}]
                               (if (:error entity)
                                 entity
                                 (result-entity-fn id)))
                             entities)]
      (assoc result :entities new-entities))
    (f fm)))

(s/defn apply-create-store-fn
  [{:keys [entities store-fn enveloped-result?] :as fm} :- FlowMap]
  (if (seq entities)
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
          (throw e))))
    fm))

(s/defn ^:private apply-store-fn :- FlowMap
  [{:keys [entities flow-type store-fn] :as fm} :- FlowMap]
  (case flow-type
    :create (apply-create-store-fn fm)
    :delete
    (assoc fm :results
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
       (remove :error)
       (map (fn [{:keys [_ id] :as entity}]
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
  [{{:keys [HooksService]} :services
    :keys [events] :as fm} :- FlowMap]
  (doseq [event events]
    ((:apply-event-hooks HooksService) event))
  fm)

(s/defn ^:private make-result :- s/Any
  [{:keys [flow-type entities results
           enveloped-result? tempids]} :- FlowMap]
  (case flow-type
    :create (if enveloped-result?
              (cond-> {:data entities}
                (seq tempids) (assoc :tempids tempids))
              entities)
    :delete (first results)
    :update (first entities)))

(s/defn patch-entities :- FlowMap
  [{:keys [prev-entity
           partial-entity
           patch-operation]
    :as fm} :- FlowMap]

  (let [patch-fn (case patch-operation
                   :add coll/add-colls
                   :remove coll/remove-colls
                   :replace coll/replace-colls
                   coll/replace-colls)
        entity (-> (deep-merge-with patch-fn
                                    prev-entity
                                    partial-entity)
                   un-store
                   (dissoc :id :schema_version))]
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
             services
             enveloped-result?]}]
  (-> {:flow-type :create
       :services services
       :entity-type entity-type
       :entities (map #(dissoc % :schema_version) entities)
       :tempids tempids
       :identity identity
       :long-id-fn long-id-fn
       :spec spec
       :realize-fn realize-fn
       :store-fn store-fn
       :create-event-fn to-create-event
       :enveloped-result? enveloped-result?}
      validate-entities
      create-ids-from-transient
      realize-entities
      throw-validation-error
      apply-before-hooks
      (preserve-errors apply-store-fn)
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
             services
             spec]}]
  (let [prev-entity (get-fn entity-id)]
    (when prev-entity
      (-> {:flow-type :update
           :entity-type entity-type
           :entities [(dissoc entity
                              :schema_version)]
           :services services
           :prev-entity prev-entity
           :identity identity
           :long-id-fn long-id-fn
           :realize-fn realize-fn
           :spec spec
           :store-fn update-fn
           :create-event-fn to-update-event}
          validate-entities
          realize-entities
          throw-validation-error
          apply-before-hooks
          apply-store-fn
          apply-long-id-fn
          create-events
          write-events
          apply-event-hooks
          apply-after-hooks
          make-result))))

(defn patch-flow
  "This function centralizes the patch workflow.
  It is helpful to easily add new hooks name
  To be noted:
    - `:before-update` hooks can modify the entity stored.
    - `:after-update` hooks are read only"
  [& {:keys [entity-type
             services
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
    (when prev-entity
      (-> {:flow-type :update
           :entity-type entity-type
           :entities []
           :services services
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
          throw-validation-error
          apply-before-hooks
          apply-store-fn
          apply-long-id-fn
          create-events
          write-events
          apply-event-hooks
          apply-after-hooks
          make-result))))

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
             long-id-fn
             services
             identity]}]
  (let [entity (get-fn entity-id)]
    (-> {:flow-type :delete
         :services services
         :entity-type entity-type
         :entities (remove nil? [entity])
         :prev-entity entity
         :identity identity
         :long-id-fn long-id-fn
         :store-fn delete-fn
         :create-event-fn to-delete-event}
        apply-before-hooks
        apply-store-fn
        apply-long-id-fn
        create-events
        write-events
        apply-event-hooks
        apply-after-hooks
        make-result)))
