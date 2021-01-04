(ns ctia.task.migration.store
  (:require [clj-momo.lib.clj-time.coerce :as time-coerce]
            [clj-momo.lib.clj-time.core :as time]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ctia.init :refer [log-properties]]
            [ctia.lib.collection :refer [fmap]]
            [ctia.lib.utils :refer [service-subgraph-from-schema]]
            [ctia.store :as store]
            [ctia.stores.es.crud :as crud]
            [ctia.stores.es.init :as es.init]
            [ctia.stores.es.mapping :as em]
            [ctia.stores.es.schemas :refer [ESConnServices ESConnState]]
            [ctia.stores.es.store :as es-store :refer [StoreMap]]
            [ctia.task.migration.migrations :refer [available-migrations]]
            [ctia.task.rollover :refer [rollover-store]]
            [ctim.domain.id :refer [long-id->id]]
            [ductile
             [conn :as conn]
             [document :as ductile.doc]
             [index :as ductile.index]
             [query :as ductile.query]]
            [ductile.schemas :refer [ESConn ESQuery Refresh]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defschema ESStoreProperties
  (st/optional-keys
   {:host s/Str
    :port s/Int
    :protocol (s/enum :http :https)
    :indexname s/Str
    :refresh Refresh
    :refresh_interval  s/Str
    :replicas s/Num
    :shards s/Num
    :rollover (st/optional-keys
               {:max_docs s/Num
                :max_age s/Str})
    :aliased  s/Bool
    :default_operator (s/enum "OR" "AND")
    :timeout s/Num
    :version s/Num}))

(s/defschema MigrationParams
  {:migration-id s/Str
   :prefix s/Str
   :migrations [(apply s/enum (keys available-migrations))]
   :store-keys [s/Keyword]
   :batch-size s/Int
   :buffer-size s/Int
   :confirm? (s/maybe s/Bool)
   :restart? (s/maybe s/Bool)
   (s/optional-key :store) {:es {s/Keyword ESStoreProperties}}})

(def timeout (* 5 60000))
(def es-max-retry 3)
;;FIXME refactor to local argument
(defonce migration-es-conn (atom nil))

(s/defschema MigrationStoreServices
  {:ConfigService {:get-config (s/=> s/Any)
                   :get-in-config (s/=>* s/Any
                                         [(s/named [s/Any] 'path)]
                                         [(s/named [s/Any] 'path)
                                          (s/named s/Any 'default)])}})

(s/defn MigrationStoreServices->ESConnServices
  :- ESConnServices
  [services :- MigrationStoreServices]
  (service-subgraph-from-schema
    services
    ESConnServices))

(defn prefixed-index [index prefix]
  (let [version-trimmed (string/replace index #"^v[^_]*_" "")]
    (format "v%s_%s" prefix version-trimmed)))

(defn target-store-properties
  [prefix store-key get-in-config]
  (let [migration-default-es-props (-> (get-in-config [:ctia :migration :store :es :default])
                                       (dissoc :indexname))]
    (cond-> (es.init/get-store-properties store-key get-in-config)
      prefix (update :indexname
                     #(prefixed-index % prefix))
      :always (-> (into migration-default-es-props)
                  (into (get-in-config [:ctia :migration :store :es store-key]))))))

(defn store-mapping
  [k]
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
              :properties (->> (keys store/empty-stores)
                               (map store-mapping)
                               (into {}))}}}})

(s/defn migration-store-properties [{{:keys [get-in-config]} :ConfigService} :- MigrationStoreServices]
  (into (target-store-properties nil :migration get-in-config)
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
  (or (retry es-max-retry ductile.doc/count-docs conn indexname)
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
   conn :- ESConn
   services :- MigrationStoreServices]
  (let [prepared (wo-storemaps migration)
        {:keys [indexname entity]} (migration-store-properties services)]
    (retry es-max-retry
           ductile.doc/index-doc
           conn
           indexname
           (name entity)
           prepared
           {:refresh "true"}))
  migration)

(def conn-overrides {:cm (conn/make-connection-manager {:timeout timeout})})

(defn store->map
  [store-record]
  (es-store/store->map store-record conn-overrides))

(def bulk-max-size (* 5 1024 1024)) ;; 5Mo

(s/defn store-map->es-conn-state :- ESConnState
  "Transforms a store map in ES lib conn state"
  [conn-state :- StoreMap
   services :- MigrationStoreServices]
  (-> conn-state 
      (set/rename-keys {:indexname :index})
      (dissoc :mapping :type :settings)
      (assoc :services (MigrationStoreServices->ESConnServices
                         services))))

(s/defn bulk-metas
  "prepare bulk data for document ids"
  [{:keys [mapping] :as store-map} ids services :- MigrationStoreServices]
  (when (seq ids)
    (-> (store-map->es-conn-state store-map services)
        (crud/get-docs-with-indices ids {})
        (->> (map #(assoc % :_type (name mapping))) ;; TODO remove after ES7 migration
             (map (fn [{:keys [_id] :as hit}]
                    {_id (select-keys hit [:_id :_index :_type])}))
             (into {})))))

(defn search-real-index?
  "Given a mapping and a document specify if the document has been modified"
  [aliased? {:keys [created modified]}]
  (boolean (and aliased?
                modified
                (not= created modified))))

(s/defn prepare-docs
  "Generates the _index, _id and _type meta data for bulk ops.
  By default we set :_index as write-index for all documents.
  In the case of aliased target, write-index is set to the write alias.
  This write alias points to last index and a document that was inserted in a previous index,
  must be updated in that same index in order to avoid its duplication.
  Thus, this function detects documents that were modified during a migration toward an aliased index,
  retrieves the actual target indices they were previously inserted in,
  and uses them to set :_index meta for these documents"
  [{:keys [mapping]
    {:keys [aliased write-index]} :props
    {:keys [version]} :conn
    :as store-map}
   docs
   services :- MigrationStoreServices]
  (let [with-metas (map #(assoc %
                                :_id (:id %)
                                :_index write-index
                                :_type mapping)
                        docs)
        {modified true not-modified false} (group-by #(search-real-index? aliased %)
                                                     with-metas)
        modified-by-ids (fmap first (group-by :id modified))
        bulk-metas-res (bulk-metas store-map (map :id modified) services)
        prepared-modified (->> bulk-metas-res
                               (merge-with into modified-by-ids)
                               vals)]
    (concat prepared-modified not-modified)))

(s/defn store-batch
  "store a batch of documents using a bulk operation"
  [{:keys [conn mapping] :as store-map}
   batch
   services :- MigrationStoreServices]
  (log/debugf "%s - storing %s records"
              mapping
              (count batch))
  (retry es-max-retry
         ductile.doc/bulk-create-doc
         conn
         (prepare-docs store-map batch services)
         {:refresh "false"}
         bulk-max-size))

(s/defn rollover?
  "Do we need to rollover? When a store is properly configured as aliased, that function
   determines if we should refresh the current write index and try a rollover. We must
   limit the number of refreshes that are costly. That function checks if the current
   number of documents in the write index is bigger than max-docs, taking into account
   that partial batches could have been inserted. Thus it tests if the current index size
   is bigger than a multiple of max_docs + a margin of `batch-size` rollovers that were
   already successfully performed."
  [aliased? max_docs batch-size migrated-count]
  (and aliased?
       max_docs
       (>= migrated-count max_docs)
       (let [margin (-> (quot migrated-count max_docs) ;; how many times we already rolled over?
                        (* batch-size))]
         (<= 0
             (rem migrated-count max_docs)
             margin))))

(s/defn rollover
  "Performs rollover if conditions are met.
Rollover requires refresh so we cannot just call ES with condition since refresh is set to -1 for performance reasons"
  [{:keys [conn mapping]
    {:keys [aliased write-index]
     {:keys [max_docs]} :rollover} :props
    :as store-map} :- StoreMap
   batch-size :- s/Int
   migrated-count :- s/Int
   services :- MigrationStoreServices]
  (when (rollover? aliased max_docs batch-size migrated-count)
    (log/info (format "%s - refreshing index %s"
                      mapping
                      write-index))
    (ductile.index/refresh! conn write-index)
    (rollover-store (store-map->es-conn-state store-map services))))

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
  [date field unit]
  {:bool
   {:filter
    {:range
     {field
      {:gte date
       :lt (str date "||+1" unit)}}}}})

(s/defn last-range-query :- ESQuery
  "returns a bool range filter query with only start limit. Add epoch_millis
  format if specified so (required  when date comes from search_after)"
  ([date field epoch-millis?]
   (last-range-query date field epoch-millis? false))
  ([date field epoch-millis? strict?]
   {:bool
    {:filter
     {:range
      {field
       (cond-> {:gte date}
         strict? (set/rename-keys {:gte :gt})
         epoch-millis? (assoc :format "epoch_millis"))}}}}))

(def Interval (s/enum "year" "month" "week" "day"))

(s/defn format-buckets :- (s/maybe [ESQuery])
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
                      (map :key_as_string))
        queries (map #(range-query % field unit)
                     (drop-last filtered))
        last-query (some-> (last filtered)
                           (last-range-query field false))]
    (cond-> (cons (missing-query field) queries)
      last-query (concat [last-query]))))

(s/defn sliced-queries :- [ESQuery]
  "this function performs a date aggregation on modification dates and returns
  bool queries that will be used to perform the migration per date ranges.
  Modification field is `modified` for entities and `timestamp` for events.
  Some documents misses the required date field and are handled with a first query
  that group them with a must_not exists bool query.
  Last range does not contains end date limit in order to handle documents
  that are created or modified during the migration. Note that new entities are now
  created with creation value as first modified value.
  The `search_after` parameter is used to handle restart where the migration
  previously ended through a bool filter range query for better performances."
  [{:keys [conn indexname mapping]} :- StoreMap
   search_after :- [s/Any]
   interval :- Interval]
  (let [agg-field (case mapping
                    "event" :timestamp
                    :modified)
        ;; This search_after value is built according to `sort`
        ;; in query-fetch-batch. The modification field is always first.
        ;; ES returns that date value as epoch milliseconds
        query (when (some->> (first search_after)
                             time-coerce/to-date)
                ;; we have a valid search_after, filter on it
                ;; set `epoch-millis?` param as true
                (last-range-query (first search_after)
                                  agg-field
                                  true
                                  true))
        aggs-q {:intervals
                {:date_histogram
                 {:field agg-field
                  :interval interval}}}
        res (retry es-max-retry
                   ductile.doc/query
                   conn
                   indexname
                   query
                   aggs-q
                   {:limit 0})
        buckets (->> res :aggs :intervals :buckets)]
    (format-buckets buckets agg-field interval)))

(def missing-date-str "2010-01-01T00:00:00.000Z")
(def missing-date-epoch (time-coerce/to-epoch missing-date-str))

(s/defn query-fetch-batch :- {s/Any s/Any}
  "fetch a batch of documents from an es index and a query"
  [query :- (s/maybe ESQuery)
   {:keys [conn indexname mapping]} :- StoreMap
   batch-size :- s/Int
   offset :- s/Int
   sort-order :- (s/maybe s/Str)
   search_after :- (s/maybe [s/Any])]
  (let [date-sort-order {"order" sort-order
                         "missing" missing-date-epoch}
        sort-by (conj (case mapping
                        "event" [{"timestamp" date-sort-order}]
                        "identity" []
                        [{"modified" date-sort-order}
                         {"created" date-sort-order}])
                      {"id" sort-order})
        params
        (merge
         {:offset (or offset 0)
          :limit batch-size}
         (when sort-order
           {:sort sort-by})
         (when search_after
           {:search_after search_after}))]
    (retry es-max-retry
           ductile.doc/query
           conn
           indexname
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
  [event-store :- StoreMap
   entity-types :- [s/Keyword]
   since :- s/Inst
   batch-size :- s/Int
   search_after :- (s/maybe [s/Any])]
  ;; TODO migrate events with mapping enabling to filter on record-type and entity.type
  (let [query {:range {:timestamp {:gte since}}}
        filter-events (fn [{:keys [event_type entity]}]
                        (and (= event_type "record-deleted")
                             (contains? (set entity-types)
                                        (-> entity :type keyword))))
        {:keys [data paging]} (query-fetch-batch query
                                                 event-store
                                                 batch-size
                                                 0
                                                 "asc"
                                                 search_after)
        deleted (->> (filter filter-events data)
                     (map :entity)
                     (map #(update % :type keyword)))]
    {:data (group-by :type deleted)
     :paging paging}))

(s/defn batch-delete
  "delete a batch of documents given their ids"
  [{:keys [conn indexname]
    entity-type :type} :- StoreMap
   ids :- [s/Str]]
  (when (seq ids)
    (ductile.index/refresh! conn indexname)
    (doseq [ids (->> (map (comp :short-id long-id->id) ids)
                     (partition-all 1000))]
      (retry es-max-retry
             ductile.doc/delete-by-query
             conn
             [indexname]
             (ductile.query/ids ids)
             {:refresh "true"
              :wait_for_completion true}))))

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
  (let [indexnames (-> (ductile.index/get conn storename)
                    keys)]
    (doseq [indexname indexnames]
      (log/infof "%s - deleting index: %s" entity-type (name indexname))
      (ductile.index/delete! conn (name indexname)))))

(defn create-target-store!
  "create the target store, pushing its template"
  [{:keys [conn indexname config props] entity-type :type}]
  (when (retry es-max-retry ductile.index/index-exists? conn indexname)
    (log/warnf "tried to create target store %s, but it already exists. Recreating it." indexname))
  (let [index-config (target-index-config indexname config props)]
    (log/infof "%s - creating index template: %s" entity-type indexname)
    (purge-store entity-type conn indexname)
    (log/infof "%s - creating store: %s" entity-type indexname)
    (retry es-max-retry ductile.index/create-template! conn indexname index-config)
    (retry es-max-retry ductile.index/create! conn (format "<%s-{now/d}-000001>" indexname) index-config)))

(s/defn init-storemap :- StoreMap
  [props :- es.init/StoreProperties
   services :- MigrationStoreServices]
  (-> props
      (es.init/init-store-conn (MigrationStoreServices->ESConnServices
                                services))
      (es-store/store-state->map conn-overrides)))

(s/defn get-target-store
  [prefix store-key {{:keys [get-in-config]} :ConfigService
                     :as services} :- MigrationStoreServices]
  (-> (target-store-properties prefix store-key get-in-config)
      (init-storemap services)))

(s/defn get-target-stores
  [prefix store-keys services :- MigrationStoreServices]
  (->> (map (fn [k]
              {k (get-target-store prefix k services)})
            store-keys)
       (into {})))

(s/defn get-source-store
  [store-key
   {{:keys [get-in-config]} :ConfigService
    :as services} :- MigrationStoreServices]
  (-> store-key
      (es.init/get-store-properties get-in-config)
      (init-storemap services)))

(s/defn get-source-stores
  [store-keys
   services :- MigrationStoreServices]
  (->> (map (fn [k]
              {k (get-source-store k services)})
            store-keys)
       (into {})))

(s/defn init-migration :- MigrationSchema
  "init the migration state, for each store it provides necessary data on source and target stores (indexname, type, source size, search_after).
when confirm? is true, it stores this state and creates the target indices."
  [{:keys [migration-id
           prefix
           store-keys
           confirm?]} :- MigrationParams
   services :- MigrationStoreServices]
  (let [source-stores (get-source-stores store-keys services)
        target-stores (get-target-stores prefix store-keys services)
        migration-properties (migration-store-properties services)
        now (time/internal-now)
        migration-stores (->> source-stores
                              (map (fn [[k v]]
                                     {k (init-migration-store v (k target-stores))}))
                              (into {}))
        migration {:id migration-id
                   :prefix prefix
                   :created now
                   :stores migration-stores}
        es-conn-state (-> migration-properties
                          (es.init/init-es-conn! (MigrationStoreServices->ESConnServices
                                                   services)))]
    (when confirm?
      (store-migration migration (:conn es-conn-state) services)
      (doseq [[_ target-store] target-stores]
        (create-target-store! target-store)))
    migration))

(s/defn ^:private with-store-map :- MigratedStore
  [entity-type :- s/Keyword
   prefix :- s/Str
   raw-store :- MigratedStore
   {{:keys [get-in-config]} :ConfigService :as services} :- MigrationStoreServices]
  (let [source-store  (-> entity-type
                          (es.init/get-store-properties get-in-config)
                          (init-storemap services))
        target-store (get-target-store prefix entity-type services)]
    (-> (assoc-in raw-store [:source :store] source-store)
        (assoc-in [:target :store] target-store))))

(s/defn update-source-size :- MigratedStore
  [raw-with-stores :- MigratedStore]
  (let [source-size (store-size (get-in raw-with-stores [:source :store]))]
    (assoc-in raw-with-stores [:source :total] source-size)))

(s/defn ^:private enrich-stores :- {s/Keyword MigratedStore}
  "enriches  migrated stores with source and target store map data,
   updates source size"
  [stores :- {s/Keyword MigratedStore}
   prefix :- s/Str
   services :- MigrationStoreServices]
  (->> (map (fn [[store-key raw]]
              {store-key (-> (with-store-map store-key prefix raw services)
                             update-source-size)})
            stores)
       (into {})))

(s/defn get-migration :- MigrationSchema
  "get migration data from it id. Updates source information,
   and add necessary store data for migration (indices, connections, etc.)"
  [migration-id :- s/Str
   es-conn :- ESConn
   services :- MigrationStoreServices]
  (let [{:keys [indexname entity]} (migration-store-properties services)
        {:keys [prefix] :as migration-raw} (retry es-max-retry
                                                  ductile.doc/get-doc
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
        (update :stores enrich-stores prefix services)
        (store-migration es-conn services))))

(s/defn update-migration-store
  ([migration-id :- s/Str
    store-key :- s/Keyword
    migrated-doc :- PartialMigratedStore
    services :- MigrationStoreServices]
   (update-migration-store migration-id
                           store-key
                           migrated-doc
                           (-> migration-es-conn deref (doto (assert "This atom is unset. Maybe some setup hasn't been performed?")))
                           services))
  ([migration-id :- s/Str
    store-key :- s/Keyword
    migrated-doc :- PartialMigratedStore
    es-conn :- ESConn
    services :- MigrationStoreServices]
   (let [partial-doc {:stores {store-key migrated-doc}}
         {:keys [indexname entity]} (migration-store-properties services)]
     (retry es-max-retry
            ductile.doc/update-doc
            es-conn
            indexname
            (name entity)
            migration-id
            partial-doc
            {:refresh "true"}))))

(s/defn finalize-migration! :- s/Any
  "reverts optimization settings of target index with configured settings.
   refresh target index.
   update migration state with completed field."
  [migration-id :- s/Str
   store-key :- s/Keyword
   source-store :- StoreMap
   target-store :- StoreMap
   es-conn :- ESConn
   services :- MigrationStoreServices]
  (log/infof "%s - update index settings" (:type source-store))
  (retry es-max-retry
         ductile.index/update-settings!
         (:conn target-store)
         (:indexname target-store)
         (revert-optimizations-settings (get-in target-store [:config :settings])))
  (log/infof "%s - trigger refresh" (:type source-store))
  (retry es-max-retry ductile.index/refresh! (:conn target-store) (:indexname target-store))
  (update-migration-store migration-id
                          store-key
                          {:completed (time/internal-now)}
                          es-conn
                          services))

(s/defn setup!
  "setup store service"
  [{{:keys [get-config]} :ConfigService :as services} :- MigrationStoreServices]
  (log/info "starting CTIA Stores...")
  (log-properties (get-config))
  (reset! migration-es-conn
          (-> (migration-store-properties services)
              (es.init/init-store-conn (MigrationStoreServices->ESConnServices
                                         services))
              :conn)))
