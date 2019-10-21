(ns ctia.task.migration.store
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ring.swagger.coerce :as sc]
            [schema.core :as s]
            [schema-tools.core :as st]
            [clj-momo.lib.clj-time
             [core :as time]
             [coerce :as time-coerce]]
            [clj-momo.lib.es
             [schemas :refer [ESConn ESQuery ESConnState]]
             [conn :as conn]
             [document :as es-doc]
             [query :as es-query]
             [index :as es-index]]
            [ctim.domain.id :refer [long-id->id]]
            [ctia.lib.collection :refer [fmap]]
            [ctia.stores.es
             [crud :as crud]
             [init :refer [init-store-conn
                           init-es-conn!
                           get-store-properties
                           StoreProperties]]
             [mapping :as em]
             [store :refer [StoreMap] :as es-store]]
            [ctia.task.rollover :refer [rollover-store]]
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

(def conn-overrides {:cm (conn/make-connection-manager {:timeout timeout})})

(defn store->map
  [store-record]
  (es-store/store->map store-record conn-overrides))

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
        (update-in [:props :write-index] prefixer)
        (update :indexname prefixer))))

(def bulk-max-size (* 5 1024 1024)) ;; 5Mo

(s/defn store-map->es-conn-state :- ESConnState
  "Transforms a store map in ES lib conn state"
  [conn-state :- StoreMap]
  (dissoc (clojure.set/rename-keys conn-state {:indexname :index})
          :mapping :type :settings))

(defn bulk-metas
  "prepare bulk data for document ids"
  [{:keys [mapping] :as store-map} ids]
  (when (seq ids)
    (-> (store-map->es-conn-state store-map)
        (crud/get-docs-with-indices (keyword mapping) ids {})
        (->> (map (fn [{:keys [_id] :as hit}]
                    {_id (select-keys hit [:_id :_index :_type])}))
             (into {})))))

(defn search-real-index?
  "Given a mapping and a document specify if the document has been modified"
  [aliased? {:keys [created modified]}]
  (boolean (and aliased?
                modified
                (not= created modified))))

(defn prepare-docs
  "Generates the _index, _id and _type meta data for bulk ops.
  By default we set :_index as write-index for all documents.
  In the case of aliased target, write-index is set to the write alias.
  This write alias points to last index and a document that was inserted in a previous index,
  must be updated in that same index in order to avoid its duplication.
  Thus, this function detects documents that were modified during a migration toward an aliased index,
  retrieves the actual target indices they were previously inserted in,
  and uses them to set :_index meta for these documents"
  [{:keys [conn mapping]
    {:keys [aliased write-index]} :props
    :as store-map}
   docs]
  (let [with-metas (map #(assoc %
                                :_id (:id %)
                                :_index write-index
                                :_type mapping)
                        docs)
        {modified true not-modified false} (group-by #(search-real-index? aliased %)
                                                     with-metas)
        modified-by-ids (fmap first (group-by :id modified))
        bulk-metas-res (->> (map :id modified)
                            (bulk-metas store-map))
        prepared-modified (->> bulk-metas-res
                               (merge-with into modified-by-ids)
                               vals)]
    (concat prepared-modified not-modified)))

(defn store-batch
  "store a batch of documents using a bulk operation"
  [{:keys [conn mapping type] :as store-map}
   batch]
  (log/debugf "%s - storing %s records"
              mapping
              (count batch))
  (retry es-max-retry
         es-doc/bulk-create-doc conn
         (prepare-docs store-map batch)
         "false"
         bulk-max-size))

(s/defn rollover?
  "do we need to rollover?"
  [aliased? max_docs batch-size migrated-count]
  (and aliased?
       max_docs
       (>= migrated-count max_docs)
       (<= 0
           (mod migrated-count max_docs)
           batch-size)))

(s/defn rollover
  "Performs rollover if conditions are met.
Rollover requires refresh so we cannot just call ES with condition since refresh is set to -1 for performance reasons"
  [{conn :conn
    {:keys [aliased write-index]
     {:keys [max_docs]} :rollover} :props
    :as store-map} :- StoreMap
   batch-size :- s/Int
   migrated-count :- s/Int]
  (when (rollover? aliased max_docs batch-size migrated-count)
    (es-index/refresh! conn write-index)
    (rollover-store (store-map->es-conn-state store-map))))

(s/defn missing-query :- ESQuery
  "implement missing filter through a must_not exists bool query
  https://www.elastic.co/guide/en/elasticsearch/reference/5.6/query-dsl-exists-query.html#missing-query"
  [field]
  {:bool
   {:must_not
    {:exists
     {:field field}}}})

(s/defn range-query :- ESQuery
  "returns a bool range filter query with start and end limits"
  [field date unit]
  {:bool
   {:filter
    {:range
     {field
      {:gte date
       :lt (str date "||+1" unit)}}}}})

(s/defn last-range-query :- ESQuery
  "returns a bool range filter query with only start limit. Add epoch_millis
  format if specified so (required  when date comes from search_after)"
  ([field date epoch-millis?]
   (last-range-query field date epoch-millis? false))
  ([field date epoch-millis? strict?]
   {:bool
    {:filter
     {:range
      {field
       (cond-> {:gte date}
         strict? (clojure.set/rename-keys {:gte :gt})
         epoch-millis? (assoc :format "epoch_millis"))}}}}))

(def missing-date-str "2010-01-01T00:00:00.000Z")
(def missing-date (time-coerce/to-date-time missing-date-str))

(defn missing-bucket?
  "Returns true if the date is related to missing date.
   Depending on date interval the date of missing bucket returned
   by ES could be either equal or before missing date."
  [date-str]
  (or (= date-str missing-date-str)
      (-> (time-coerce/to-date-time date-str)
          (time/before? missing-date))))

(def Interval (s/enum "year" "month" "week" "day"))

(s/defn format-buckets :- [ESQuery]
  "format buckets from aggregation results into an ordered list of proper bool queries"
  [raw-buckets :- [(st/open-schema
                    {:doc_count s/Int
                     :key_as_string s/Str})]
   field :- s/Keyword
   interval :- Interval]
  (let [unit (case interval
               "year" "y"
               "month" "M"
               "week" "w"
               "day" "d")
        filtered (->> raw-buckets
                      (filter #(< 0 (:doc_count %)))
                      (map :key_as_string))]
    (loop [dates filtered
           queries []]
      (if-let [date (first dates)]
        (->>
         (cond
           (missing-bucket? date) (missing-query field)
           (next dates) (range-query field date unit)
           :else (last-range-query field date false))
         (conj queries)
         (recur (next dates)))
        queries))))

(s/defn sliced-queries :- [ESQuery]
  "this function performs a date aggregation on modification dates and returns
  bool queries that will be used to perform the migration per date ranges.
  modification field is `modified` for entities and `timestamp` for events.
  Some documents misses the required date field and are handled with a first query
  that group them with a must_not exists bool query.
  Last range does not contains end date limit in order to handle documents
  that are created or modified during the migration. Note that new entities are now
  created with creation value as first modified value"
  [{:keys [conn indexname mapping]} :- StoreMap
   search_after :- [s/Any]
   interval :- Interval]
  (let [agg-field (case mapping
                    "event" :timestamp
                    :modified)
        query (when (some->> (first search_after)
                             time-coerce/to-date
                             (time/after? (time/internal-now)))
                ;; we have a valid search_after, filter on it
                (last-range-query agg-field
                                  (first search_after)
                                  true
                                  true))
        aggs-q {:intervals
                {:date_histogram
                 {:field agg-field
                  :interval interval
                  :missing missing-date-str}}}
        res (retry es-max-retry
                   es-doc/query
                   conn
                   indexname
                   mapping
                   query
                   aggs-q
                   {:limit 0})
        buckets (->> res :aggs :intervals :buckets)]
    (format-buckets buckets agg-field interval)))

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
           es-doc/query
           conn
           indexname
           mapping
           query
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
             [indexname]
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
      (assoc :aliases {(:write-index props) {}
                       indexname {}})))

(defn revert-optimizations-settings
  "Revert configuration settings used for speeding up migration"
  [settings]
  (let [res (into {:refresh_interval "1s"}
                  (select-keys settings
                               [:number_of_replicas :refresh_interval]))]
    {:index res}))

(defn purge-store
  [entity-type conn storename]
  (log/infof "%s - purging store: %s" entity-type storename)
  (let [indexnames (-> (es-index/get conn storename)
                    keys)]
    (doseq [indexname indexnames]
      (log/infof "%s - deleting index: %s" entity-type (name indexname))
      (es-index/delete! conn (name indexname)))))

(defn create-target-store!
  "create the target store, pushing its template"
  [{:keys [conn indexname config props] entity-type :type :as target-store}]
  (when (retry es-max-retry es-index/index-exists? conn indexname)
    (log/warnf "tried to create target store %s, but it already exists. Recreating it." indexname))
  (let [index-config (target-index-config indexname config props)]
    (log/infof "%s - creating index template: %s" entity-type indexname)
    (purge-store entity-type conn indexname)
    (log/infof "%s - creating store: %s" entity-type indexname)
    (retry es-max-retry es-index/create-template! conn indexname index-config)
    (retry es-max-retry es-index/create! conn (format "<%s-{now/d}-000001>" indexname) index-config)))

(defn target-store-properties
  [prefix store-key]
  (-> (get-store-properties store-key)
      (update :indexname
              #(prefixed-index % prefix))))

(s/defn init-storemap :- StoreMap
  [props :- StoreProperties]
  (-> (init-store-conn props)
      (es-store/store-state->map conn-overrides)))

(defn get-target-store
  [prefix store-key]
  (init-storemap (target-store-properties prefix store-key)))

(defn get-target-stores
  [prefix store-keys]
  (->> (map (fn [k]
              {k (get-target-store prefix k)})
            store-keys)
       (into {})))

(s/defn init-migration :- MigrationSchema
  "init the migration state, for each store it provides necessary data on source and target stores (indexname, type, source size, search_after).
when confirm? is true, it stores this state and creates the target indices."
  [migration-id :- s/Str
   prefix :- s/Str
   store-keys :- [s/Keyword]
   confirm? :- s/Bool]
  (let [source-stores (stores->maps (select-keys @stores store-keys))
        target-stores (get-target-stores prefix store-keys)
        migration-properties (migration-store-properties)
        now (time/internal-now)
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
        target-store (get-target-store prefix entity-type)]
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
                          {:completed (time/internal-now)}
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
