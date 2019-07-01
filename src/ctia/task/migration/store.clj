(ns ctia.task.migration.store
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ring.swagger.coerce :as sc]
            [schema.core :as s]
            [schema-tools.core :as st]
            [clj-momo.lib.time :as time]
            [clj-momo.lib.es
             [schemas :refer [ESConn ESQuery ESConnState]]
             [conn :as conn]
             [document :as es-doc]
             [query :as es-query]
             [index :as es-index]]
            [ctim.domain.id :refer [long-id->id]]
            [ctia.stores.es
             [crud :as crud]
             [init :refer [init-store-conn init-es-conn! get-store-properties]]
             [mapping :as em]
             [store :refer [StoreMap] :as es-store]]
            [ctia.lib.collection :refer [fmap]]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :refer [properties init!]]
             [store :refer [stores]]]))

(def timeout (* 5 60000))
(def es-max-retry 3)
(def migration-es-conn (atom nil))

(def token-mapping
  (dissoc em/token :fielddata))

(defn store-mapping
  [[k store]]
  {k {:type "object"
      :properties {:source {:type "object"
                            :properties
                            {:index em/token
                             :total {:type "long"}}}
                   :target {:type "object"
                            :properties
                            {:index em/token
                             :migrated {:type "long"}}}
                   :started em/ts
                   :completed em/ts}}})

(def migration-mapping
  {"migration"
   {:dynamic false
    :properties
    {:id em/token
     :timestamp em/ts
     :stores {:type "object"
              :properties (->> (map store-mapping @stores)
                               (into {}))}}}})

(defn migration-store-properties []
  (into (get-store-properties :migration)
        {:shards 1
         :replicas 1
         :refresh true
         :aliased false
         :mappings migration-mapping}))

(s/defschema SourceState
  {:index s/Str
   :total s/Int
   (s/optional-key :search_after) [s/Any]
   (s/optional-key :store) StoreMap})

(s/defschema TargetState
  {:index s/Str
   :migrated s/Int
   (s/optional-key :store) StoreMap})

(s/defschema MigratedStore
  {:source SourceState
   :target TargetState
   (s/optional-key :started) s/Inst
   (s/optional-key :completed) s/Inst})

(s/defschema PartialMigratedStore
  (st/optional-keys
   {:source (st/optional-keys SourceState)
    :target (st/optional-keys TargetState)
    (s/optional-key :started) s/Inst
    (s/optional-key :completed) s/Inst}))

(s/defschema MigrationSchema
  {:id s/Str
   :created java.util.Date
   :prefix s/Str
   :stores {s/Keyword MigratedStore}})

(defn retry
  [retries f & args]
  (let [res (try {:value (apply f args)}
                 (catch Exception e
                   (if (zero? retries)
                     (throw e)
                     {:exception e})))]
    (if (:exception res)
      (recur (dec retries) f args)
      (:value res))))

(defn store-size
  [{:keys [conn indexname mapping]}]
  (or (retry es-max-retry es-doc/count-docs conn indexname mapping)
      0))

(defn init-migration-store
  [source target]
  {:source {:index (:indexname source)
            :total (store-size source)
            :store source}
   :target {:index (:indexname target)
            :migrated 0
            :store target}})

(s/defn wo-storemaps :- MigrationSchema
  [{:keys [stores] :as migration} :- MigrationSchema]
  (assoc migration
         :stores
         (fmap #(-> (update % :source dissoc :store)
                    (update :target dissoc :store))
               stores)))

(s/defn store-migration
  [migration :- MigrationSchema
   conn :- ESConn]
  (let [prepared (wo-storemaps migration)
        {:keys [indexname entity]} (migration-store-properties)]
    (retry es-max-retry
           es-doc/create-doc conn
           indexname
           (name entity)
           prepared
           "true"))
  migration)

(defn prefixed-index [index prefix]
  (let [version-trimmed (string/replace index #"^v[^_]*_" "")]
    (format "v%s_%s" prefix version-trimmed)))

(defn store->map
  [store-record]
  (es-store/store->map store-record
              {:cm (conn/make-connection-manager {:timeout timeout})}))

(defn stores->maps
  "transform store records to maps"
  [stores]
  (into {}
        (map (fn [[store-key store-record]]
               {store-key (store->map store-record)})
             stores)))

(defn source-store-map->target-store-map
  "transform a source store map into a target map,
  essentially updating indexname"
  [store prefix]
  (let [prefixer #(prefixed-index % prefix)
        aliases (->> (get-in store [:config :aliases])
                     (map (fn [[k v]] {(prefixer k) v}))
                     (into {}))]
    (-> (assoc-in store [:config :aliases] aliases)
        (update-in [:props :write-alias] prefixer)
        (update :indexname prefixer))))


(defn source-store-maps->target-store-maps
  "transform target store records to maps"
  [source-stores prefix]
  (into {}
        (map (fn [[sk sr]]
               {sk (source-store-map->target-store-map sr prefix)})
             source-stores)))

(def bulk-max-size (* 5 1024 1024)) ;; 5Mo

(s/defn store-map->es-conn-state :- ESConnState
  "Transforms a store map in ES lib conn state"
  [conn-state :- StoreMap]
  (dissoc (clojure.set/rename-keys conn-state {:indexname :index})
          :mapping :type :settings))


(defn real-index-of
  "Retrieves the real index of a document"
  [{:keys [mapping] :as conn-state} id]
  (-> (store-map->es-conn-state conn-state)
      (crud/get-doc-with-index (keyword mapping) id {})
      :_index))

(defn write-alias
  "Generates the index or alias on which a write operation should be performed."
  [{:keys [conn props mapping] :as conn-state}
   {:keys [id created modified] :as doc}]
  (if (or (= :event (keyword mapping))
          (= created modified)
          (nil? modified))
    (:write-alias props)
    (real-index-of conn-state id)))

(s/defn rollover?
  "do we need to rollover?"
  [aliased? max_docs batch-size migrated-count]
  (and aliased?
       max_docs
       (> migrated-count max_docs)
       (<= (mod migrated-count max_docs)
           batch-size)))

(s/defn rollover
  "Performs rollover if conditions are met.
Rollover requires refresh so we cannot just call ES with condition since refresh is set to -1 for performance reasons"
  [{conn :conn
    {:keys [aliased write-alias]
     {:keys [max_docs]} :rollover} :props
    :as store-map} :- StoreMap
   batch-size :- s/Int
   migrated-count :- s/Int]
  (when rollover?
    (es-index/refresh! conn write-alias)
    (crud/rollover (store-map->es-conn-state store-map))))

(defn store-batch
  "store a batch of documents using a bulk operation"
  [{:keys [conn mapping type] :as store-map}
   batch]
  (log/debugf "%s - storing %s records"
              mapping
              (count batch))
  (let [prepared-docs
        (map #(assoc %
                     :_id (:id %)
                     :_index (write-alias store-map %)
                     :_type mapping)
             batch)
        res (retry es-max-retry
                   es-doc/bulk-create-doc conn
                   prepared-docs
                   "false"
                   bulk-max-size)]
    res))

(s/defn query-fetch-batch :- {s/Any s/Any}
  "fetch a batch of documents from an es index and a query"
  [query :- (s/maybe ESQuery)
   {:keys [conn indexname mapping]} :- StoreMap
   batch-size :- s/Int
   offset :- s/Int
   sort-order :- (s/maybe s/Str)
   search_after :- (s/maybe [s/Any])]
  (let [sort-by (conj (case mapping
                        "event" [{"timestamp" sort-order}]
                        "identity" []
                        [{"modified" sort-order}
                         {"created" sort-order}])
                      {"_uid" sort-order})
        params
        (merge
         {:offset (or offset 0)
          :limit batch-size}
         (when sort-order
           {:sort sort-by})
         (when search_after
           {:search_after search_after}))]
    (retry es-max-retry
           es-doc/search-docs conn
           indexname
           mapping
           query
           {}
           params)))

(s/defn fetch-batch :- {s/Any s/Any}
  "fetch a batch of documents from an es index"
  [store :- StoreMap
   batch-size :- s/Int
   offset :- s/Int
   sort-order :- (s/maybe s/Str)
   search_after :- (s/maybe [s/Any])]
  (query-fetch-batch nil store batch-size offset sort-order search_after))

(s/defn fetch-deletes :- (s/maybe {s/Any s/Any})
  "retrieves delete events for given entity types and since given date"
  [entity-types :- [s/Keyword]
   since :- s/Inst
   batch-size :- s/Int
   search_after :- (s/maybe [s/Any])]
  ;; TODO migrate events with mapping enabling to filter on record-type and entity.type
  (let [query {:range {:timestamp {:gte since}}}
        event-store (store->map (:event @stores))
        filter-events (fn [{:keys [event_type entity]}]
                        (and (= event_type "record-deleted")
                             (contains? (set entity-types)
                                        (-> entity :type keyword))))]
      (let [{:keys [data paging]} (query-fetch-batch query
                                                     event-store
                                                     batch-size
                                                     0
                                                     "asc"
                                                     search_after)
            deleted (->> (filter filter-events data)
                         (map :entity)
                         (map #(update % :type keyword)))]
        {:data (group-by :type deleted)
         :paging paging})))

(s/defn batch-delete
  "delete a batch of documents given their ids"
  [{:keys [conn indexname]
    entity-type :type :as store} :- StoreMap
   ids :- [s/Str]]
  (when (seq ids)
    (es-index/refresh! conn indexname)
    (doseq [ids (->> (map (comp :short-id long-id->id) ids)
                     (partition-all 1000))]
      (retry es-max-retry
             es-doc/delete-by-query
             conn
             indexname
             (name entity-type)
             (es-query/ids ids)
             true
             "true"))))

(defn target-index-config
  "Generates the configuration of an index while migrating"
  [indexname config props]
  (-> (update config
              :settings
              assoc
              :number_of_replicas 0
              :refresh_interval -1)
      (assoc :aliases {(:write-alias props) {}
                       indexname {}})))

(defn revert-optimizations-settings
  "Revert configuration settings used for speeding up migration"
  [settings]
  (let [res (into {:refresh_interval "1s"}
                  (select-keys settings
                               [:number_of_replicas :refresh_interval]))]
    {:index res}))

(defn create-target-store!
  "create the target store, pushing its template"
  [{:keys [conn indexname config props] entity-type :type :as target-store}]
  (when (retry es-max-retry es-index/index-exists? conn indexname)
    (log/warnf "tried to create target store %s, but it already exists. Recreating it." indexname))
  (let [index-config (target-index-config indexname config props)]
    (log/infof "%s - purging indexes: %s" entity-type indexname)
    (retry es-max-retry es-index/delete! conn (str indexname "*"))
    (log/infof "%s - creating index template: %s" entity-type indexname)
    (log/infof "%s - creating index: %s" entity-type indexname)
    (retry es-max-retry es-index/create-template! conn indexname index-config)
    (retry es-max-retry es-index/create! conn (str indexname "-000001") index-config)))

(s/defn init-migration :- MigrationSchema
  "init the migration state, for each store it provides necessary data on source and target stores (indexname, type, source size, search_after).
when confirm? is true, it stores this state and creates the target indices."
  [migration-id :- s/Str
   prefix :- s/Str
   store-keys :- [s/Keyword]
   confirm? :- s/Bool]

  (let [source-stores (stores->maps (select-keys @stores store-keys))
        target-stores
        (source-store-maps->target-store-maps source-stores prefix)
        migration-properties (migration-store-properties)
        now (time/now)
        migration-stores (->> source-stores
                              (map (fn [[k v]]
                                     {k (init-migration-store v (k target-stores))}))
                              (into {}))
        migration {:id migration-id
                   :prefix prefix
                   :created now
                   :stores migration-stores}
        es-conn-state (init-es-conn! migration-properties)]
    (when confirm?
      (store-migration migration (:conn es-conn-state))
      (doseq [[_ target-store] target-stores]
              (create-target-store! target-store)))
    migration))

(s/defn with-store-map :- MigratedStore
  [entity-type :- s/Keyword
   prefix :- s/Str
   {source :source
    target :target :as raw-store} :- MigratedStore]
  (let [source-store (store->map (get @stores entity-type))
        target-store (source-store-map->target-store-map source-store prefix)]
    (-> (assoc-in raw-store [:source :store] source-store)
        (assoc-in [:target :store] target-store))))

(s/defn update-source-size :- MigratedStore
  [raw-with-stores :- MigratedStore]
  (let [source-size (store-size (get-in raw-with-stores [:source :store]))]
    (assoc-in raw-with-stores [:source :total] source-size)))

(s/defn enrich-stores :- {s/Keyword MigratedStore}
  "enriches  migrated stores with source and target store map data,
   updates source size"
  [stores :- {s/Keyword MigratedStore}
   prefix :- s/Str]
  (->> (map (fn [[store-key raw]]
              {store-key (-> (with-store-map store-key prefix raw)
                             update-source-size)})
            stores)
       (into {})))

(s/defn get-migration :- MigrationSchema
  "get migration data from it id. Updates source information,
   and add necessary store data for migration (indices, connections, etc.)"
  [migration-id :- s/Str
   es-conn :- ESConn]
  (let [{:keys [indexname entity]} (migration-store-properties)
        {:keys [prefix] :as migration-raw} (retry es-max-retry
                                                  es-doc/get-doc
                                                  es-conn
                                                  indexname
                                                  (name entity)
                                                  migration-id
                                                  {})
        coerce (crud/coerce-to-fn MigrationSchema)]
    (when-not migration-raw
      (log/errorf "migration not found: %s" migration-id)
      (throw (ex-info "migration not found" {:id migration-id})))
    (-> (coerce migration-raw)
        (update :stores enrich-stores prefix)
        (store-migration es-conn))))

(s/defn update-migration-store
  ([migration-id :- s/Str
    store-key :- s/Keyword
    migrated-doc :- PartialMigratedStore]
   (update-migration-store migration-id store-key migrated-doc @migration-es-conn))
  ([migration-id :- s/Str
    store-key :- s/Keyword
    migrated-doc :- PartialMigratedStore
    es-conn :- ESConn]
   (let [partial-doc {:stores {store-key migrated-doc}}
         {:keys [indexname entity]} (migration-store-properties)]
     (retry es-max-retry
            es-doc/update-doc
            es-conn
            indexname
            (name entity)
            migration-id
            partial-doc
            "true"))))

(s/defn finalize-migration! :- s/Any
  "reverts optimization settings of target index with configured settings.
   refresh target index.
   update migration state with completed field."
  [migration-id :- s/Str
   store-key :- s/Keyword
   source-store :- StoreMap
   target-store :- StoreMap
   es-conn :- ESConn]
  (log/infof "%s - update index settings" (:type source-store))
  (retry es-max-retry
         es-index/update-settings!
         (:conn target-store)
         (:indexname target-store)
         (revert-optimizations-settings (get-in target-store [:config :settings])))
  (log/infof "%s - trigger refresh" (:type source-store))
  (retry es-max-retry es-index/refresh! (:conn target-store) (:indexname target-store))
  (update-migration-store migration-id
                          store-key
                          {:completed (time/now)}
                          es-conn))

(defn setup!
  "init properties, start CTIA and its store service"
  []
  (log/info "starting CTIA Stores...")
  (init!)
  (log-properties)
  (init-store-service!)
  (->> (migration-store-properties)
       init-store-conn
       :conn
       (reset! migration-es-conn)))
