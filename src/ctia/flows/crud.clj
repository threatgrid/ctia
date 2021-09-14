(ns ctia.flows.crud
  "This namespace handle all necessary flows for creating, updating
  and deleting entities."
  (:require
   [clj-momo.lib.map :refer [deep-merge-with]]
   [clojure.set :refer [index]]
   [clojure.spec.alpha :as cs]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [ctia.auth :as auth]
   [ctia.domain.access-control :refer [allowed-tlp? allowed-tlps]]
   [ctia.domain.entities :refer [un-store]]
   [ctia.entity.event.obj-to-event :refer
    [to-create-event to-delete-event to-update-event]]
   [ctia.lib.collection :as coll]
   [ctia.properties :as p]
   [ctia.schemas.core :as schemas :refer
    [APIHandlerServices
     APIHandlerServices->RealizeFnServices
     HTTPShowServices
     RealizeFn
     lift-realize-fn-with-context
     TempIDs]]
   [ctia.store :as store]
   [ctim.domain.id :as id]
   [ring.util.http-response :as http-response]
   [schema.core :as s])
  (:import java.util.UUID))

(s/defschema FlowMap
  {:create-event-fn                       (s/pred fn?)
   :entities                              [{s/Keyword s/Any}]
   :entity-type                           s/Keyword
   :flow-type                             (s/enum :create :update :delete)
   :services                              APIHandlerServices
   :identity                              (s/protocol auth/IIdentity)
   :store-fn                              (s/=> s/Any s/Any)
   (s/optional-key :get-success-entities) (s/pred fn?)
   (s/optional-key :events)               [{s/Keyword s/Any}]
   (s/optional-key :long-id-fn)           (s/maybe (s/=> s/Any s/Any))
   (s/optional-key :get-prev-entity)      (s/pred fn?)
   (s/optional-key :partial-entities)     [(s/maybe {s/Keyword s/Any})]
   (s/optional-key :patch-operation)      (s/enum :add :remove :replace)
   (s/optional-key :realize-fn)           RealizeFn
   (s/optional-key :find-entity-id)       (s/pred fn?)
   (s/optional-key :results)              s/Any
   (s/optional-key :spec)                 (s/maybe s/Keyword)
   (s/optional-key :tempids)              (s/maybe TempIDs)
   (s/optional-key :enveloped-result?)    (s/maybe s/Bool)
   (s/optional-key :make-result)        (s/maybe (s/pred fn?))
   (s/optional-key :entity-ids)           [s/Str]
   (s/optional-key :not-found)            [s/Str]})

(defn gen-random-uuid []
  (UUID/randomUUID))

(defn make-id
  [entity-type]
  (str (name entity-type) "-" (gen-random-uuid)))

(s/defn ^:private find-checked-id
  "checks that the hostname in the ID (if it is a long ID)
   is the local server hostname. Throws bad-request! on mismatch."
  [{id :id :as entity}
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

(s/defn find-create-entity-id
  [services :- HTTPShowServices]
  (s/fn [{identity-obj :identity
          :keys [entity-type tempids]} :- FlowMap
         entity] :- s/Str
    (or
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
     (make-id entity-type))))

(s/defn ^:private find-existing-entity-id
  [prev-entity-fn]
  (s/fn [_fm :- FlowMap
         {id :id :as _entity}] :- (s/maybe s/Str)
    (if (and (seq id) (prev-entity-fn id))
      (id/str->short-id id)
      id)))

(defn- check-spec [entity spec]
  (if (and spec
           (not (cs/valid? spec
                           ;; the spec enforce long id for the API
                           ;; while we store short ids
                           (dissoc entity :id))))
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
  [{{{:keys [get-in-config]} :ConfigService} :services
    :keys [spec entities] :as fm} :- FlowMap]
  (assoc fm :entities
         (map (fn [entity]
                (-> entity
                    (check-spec spec)
                    (tlp-check get-in-config)))
              entities)))

(s/defn ^:private create-ids-from-transient :- FlowMap
  "Creates IDs for entities identified by transient IDs that have not
   yet been resolved."
  [{:keys [entities
           entity-type]
    :as fm} :- FlowMap]
  (let [newtempids
        (->> entities
             (keep (fn [{:keys [id]}]
                     (when (and id (schemas/transient-id? id))
                       [id (make-id entity-type)])))
             (into {}))]
    (update fm :tempids (fnil into {}) newtempids)))

(s/defn ^:private realize-entities :- FlowMap
  [{:keys [entities
           flow-type
           identity
           services
           tempids
           find-entity-id
           get-prev-entity] :as fm} :- FlowMap]
  (let [identity-map (auth/ident->map identity)
        realize-fn (lift-realize-fn-with-context
                     (:realize-fn fm)
                     {:services (APIHandlerServices->RealizeFnServices
                                  services)})]
    (assoc fm
           :entities
           (doall
            (for [entity entities
                  :let [entity-id (find-entity-id fm entity)]]
              (cond
                (:error entity) entity
                (:error entity-id) entity-id
                :else (case flow-type
                        :create (realize-fn entity
                                            entity-id
                                            tempids
                                            identity-map)
                        :update
                        (if-let [prev-entity (get-prev-entity entity-id)]
                          (realize-fn entity
                                      entity-id
                                      tempids
                                      identity-map
                                      prev-entity)
                          (realize-fn entity
                                      entity-id
                                      tempids
                                      identity-map))
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
    :keys [entities flow-type get-prev-entity] :as fm} :- FlowMap]
  (assoc fm
         :entities
         (doall
          (for [entity entities]
            (cond-> {:entity entity
                     :hook-type (case flow-type
                                  :create :before-create
                                  :update :before-update
                                  :delete :before-delete)
                     :read-only? (= flow-type :delete)}
              get-prev-entity (assoc :prev-entity (get-prev-entity (:id entity)))
              :finally apply-hooks)))))

(s/defn ^:private apply-after-hooks :- FlowMap
  [{{{:keys [apply-hooks]} :HooksService} :services
    :keys [entities flow-type get-prev-entity] :as fm} :- FlowMap]
  (doseq [entity entities]
    (cond-> {:entity entity
             :hook-type (case flow-type
                          :create :after-create
                          :update :after-update
                          :delete :after-delete)
             :read-only? true}
      get-prev-entity (assoc :prev-entity (get-prev-entity (:id entity)))
      :finally apply-hooks))
  fm)
;; TODO for vs doseq
;; why apply-before-hooks returns the result of the for
;; while apply-after-hooks returns the fm as it is?

(defn default-success-entities
  [fm]
  (->> fm :entities (remove :error)))

(s/defn ^:private create-events :- FlowMap
  [{:keys [create-event-fn
           flow-type
           identity
           get-prev-entity
           get-success-entities]
    :or {get-success-entities default-success-entities}
    {{:keys [get-in-config]} :ConfigService}
    :services
    :as fm} :- FlowMap]
  (if (get-in-config [:ctia :events :enabled])
    (let [login (auth/login identity)
          create-event (fn [entity]
                         (let [event-id (make-id "event")]
                           (try
                             (if (= flow-type :update)
                               (create-event-fn entity
                                                (get-prev-entity (:id entity))
                                                event-id login)
                               (create-event-fn entity event-id login))
                             (catch Throwable e
                               (log/error "Could not create event" e)
                               (throw (ex-info "Could not create event"
                                               {:flow-type flow-type
                                                :login login
                                                :entity entity}))))))
          events (->> (get-success-entities fm)
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
  (update fm :entities #(remove :error %)))

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

(s/defn apply-delete-store-fn
  [{:keys [entity-ids store-fn] :as fm} :- FlowMap]
  (assoc fm :results (store-fn entity-ids)))

(s/defn apply-update-store-fn
  [{:keys [store-fn entities] :as fm} :- FlowMap]
  (assoc fm :results (store-fn entities)))

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

(s/defn make-create-result :- s/Any
  [{:keys [entities enveloped-result? tempids]} :- FlowMap]
  (if enveloped-result?
    (cond-> {:data entities}
      (seq tempids) (assoc :tempids tempids))
    entities))

(defn patch-entity
  [patch-fn prev-entity partial-entity]
  (when prev-entity
    (-> (deep-merge-with patch-fn
                         prev-entity
                         (dissoc partial-entity :id))
        un-store
        (dissoc :schema_version))))

(s/defn patch-entities :- FlowMap
  [{:keys [get-prev-entity
           partial-entities
           patch-operation]
    :as fm} :- FlowMap]
  (let [patch-fn (case patch-operation
                   :add coll/add-colls
                   :remove coll/remove-colls
                   :replace coll/replace-colls
                   coll/replace-colls)
        entities (for [partial-entity partial-entities
                       :let [prev-entity (some->> partial-entity
                                                  :id
                                                  get-prev-entity)]

                       :when (some? prev-entity)]
                   (patch-entity patch-fn prev-entity partial-entity))
        not-found (remove #(some->> % :id get-prev-entity) partial-entities)]
    (assoc fm
           :entities entities
           :not-found (map :id not-found))))

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
             enveloped-result?
             get-success-entities]}]
  (-> {:flow-type :create
       :services services
       :entity-type entity-type
       :entities (map #(dissoc % :schema_version) entities)
       :tempids tempids
       :identity identity
       :long-id-fn long-id-fn
       :spec spec
       :realize-fn realize-fn
       :find-entity-id (find-create-entity-id services)
       :store-fn store-fn
       :create-event-fn to-create-event
       :enveloped-result? enveloped-result?
       :get-success-entities (or get-success-entities default-success-entities)}
      validate-entities
      create-ids-from-transient
      realize-entities
      throw-validation-error
      apply-before-hooks
      (preserve-errors apply-create-store-fn)
      apply-long-id-fn
      create-events
      write-events
      apply-event-hooks
      apply-after-hooks
      make-create-result))

(defn prev-entity
  [get-fn ids]
  (let [indexed (->> (get-fn ids)
                     (filter seq)
                     (into {}
                           (map (fn [e]
                                  [(id/str->short-id (:id e)) e]))))]
    (fn [id]
      (when (seq id)
        (get indexed (id/str->short-id id))))))

(s/defn make-update-result
  [{:keys [make-result results long-id-fn] :as fm} :- FlowMap]
  (if make-result
    (make-result fm)
    (map long-id-fn results)))

(s/defn make-delete-result
  [{:keys [make-result results] :as fm} :- FlowMap]
  (if make-result
    (make-result fm)
    results))

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
             identity
             entities
             long-id-fn
             services
             spec
             get-success-entities
             make-result]
      :or {get-success-entities default-success-entities}}]
  (let [ids (map :id entities)
        prev-entity-fn (prev-entity get-fn ids)]
    (-> {:flow-type :update
         :entity-type entity-type
         :entities (map #(dissoc % :schema_version) entities)
         :services services
         :get-prev-entity prev-entity-fn
         :identity identity
         :long-id-fn long-id-fn
         :realize-fn realize-fn
         :find-entity-id (find-existing-entity-id prev-entity-fn)
         :spec spec
         :store-fn update-fn
         :create-event-fn to-update-event
         :get-success-entities get-success-entities
         :make-result make-result}
        validate-entities
        realize-entities
        throw-validation-error
        apply-before-hooks
        apply-update-store-fn
        apply-long-id-fn
        create-events
        write-events
        apply-event-hooks
        apply-after-hooks
        make-update-result)))

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
             identity
             patch-operation
             partial-entities
             long-id-fn
             spec
             get-success-entities
             make-result]
      :or {get-success-entities default-success-entities}}]
  (let [ids (map :id partial-entities)
        prev-entity-fn (prev-entity get-fn ids)]
    (-> {:flow-type :update
         :entity-type entity-type
         :entities []
         :services services
         :get-prev-entity prev-entity-fn
         :partial-entities partial-entities
         :patch-operation patch-operation
         :identity identity
         :long-id-fn long-id-fn
         :realize-fn realize-fn
         :find-entity-id (find-existing-entity-id prev-entity-fn)
         :spec spec
         :store-fn update-fn
         :create-event-fn to-update-event
         :get-success-entities get-success-entities
         :make-result make-result}
        patch-entities
        validate-entities
        realize-entities
        throw-validation-error
        apply-before-hooks
        apply-update-store-fn
        apply-long-id-fn
        create-events
        write-events
        apply-event-hooks
        apply-after-hooks
        make-update-result)))

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
             entity-ids
             long-id-fn
             services
             identity
             get-success-entities
             make-result]}]
  (let [entities (get-fn entity-ids)]
    (-> {:flow-type :delete
         :services services
         :entity-type entity-type
         :entities (remove nil? entities)
         :entity-ids entity-ids
         :identity identity
         :get-success-entities (or get-success-entities default-success-entities)
         :long-id-fn long-id-fn
         :store-fn delete-fn
         :create-event-fn to-delete-event
         :make-result make-result}
        apply-before-hooks
        apply-delete-store-fn
        apply-long-id-fn
        create-events
        write-events
        apply-event-hooks
        apply-after-hooks
        make-delete-result)))
