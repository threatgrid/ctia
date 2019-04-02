(ns ctia.task.migration.store
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [schema-tools.core :as st]
            [clj-momo.lib.time :as time]
            [clj-momo.lib.es
             [schemas :refer [ESConn ESConnState]]
             [conn :as conn]
             [document :as es-doc]
             [index :as es-index]]
            [ctia.stores.es
             [init :refer [init-store-conn init-es-conn! get-store-properties]]
             [mapping :as em]
             [store :refer [StoreMap] :as es-store]]
            [ctia.lib.collection :refer [fmap]]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :refer [properties init!]]
             [store :refer [stores]]]))

(def timeout (* 5 60000))

(def migration-conn-state (atom nil))

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
                               (apply merge))}}}})

(defn migration-store-properties
  []
  (-> (get-store-properties :migration)
      (merge
       {:shards 1
        :replicas 1
        :refresh true
        :mappings migration-mapping})))

(s/defschema MigratedStore
  {:source {:index s/Str
            :total s/Int
            (s/optional-key :search_after) s/Any
            :store StoreMap}
   :target {:index s/Str
            :migrated s/Int
            :store StoreMap}
   (s/optional-key :started) s/Inst
   (s/optional-key :completed) s/Inst})

(s/defschema PartialMigratedStore
  (st/optional-keys
   {:source (st/optional-keys {:index s/Str
                               :total s/Int
                               :search_after s/Any
                               :store StoreMap})
    :target (st/optional-keys {:index s/Str
                               :migrated s/Int
                               :store StoreMap})
    (s/optional-key :started) s/Inst
    (s/optional-key :completed) s/Inst}))

(s/defschema MigrationSchema
  {:id s/Str
   :created java.util.Date
   :stores {s/Keyword MigratedStore}})

(defn store-size
  [{:keys [conn indexname mapping]}]
  (es-doc/count-docs conn indexname mapping))

(defn init-migration-store
  [source target]
  {:source {:index (:indexname source)
            :total (or (store-size source) 0)
            :store source}
   :target {:index (:indexname target)
            :migrated 0
            :store target}})

(s/defn wo-storemaps
  [{:keys [stores] :as migration} :- MigrationSchema]
  (assoc migration
         :stores
         (fmap #(-> (update-in % [:source] dissoc :store)
                    (update-in [:target] dissoc :store))
               stores)))

(s/defn store-migration
  [migration :- MigrationSchema
   conn :- ESConn]
  (let [prepared (wo-storemaps migration)
        {:keys [indexname entity]} (migration-store-properties)]
    (es-doc/create-doc conn
                       indexname
                       (name entity)
                       prepared
                       "true")))

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
  (update store :indexname #(prefixed-index % prefix)))


(defn source-store-maps->target-store-maps
  "transform target store records to maps"
  [source-stores prefix]
  (into {}
        (map (fn [[sk sr]]
               {sk (source-store-map->target-store-map sr prefix)})
             source-stores)))

(def bulk-max-size (* 5 1024 1024)) ;; 5Mo

(defn store-batch
  "store a batch of documents using a bulk operation"
  [{:keys [conn indexname mapping type]} batch]
  (log/debugf "%s - storing %s records"
              type
              (count batch))
  (let [prepared-docs
        (map #(assoc %
                     :_id (:id %)
                     :_index indexname
                     :_type mapping)
             batch)]

    (es-doc/bulk-create-doc
     conn
     prepared-docs
     "false"
     bulk-max-size)))

(defn fetch-batch
  "fetch a batch of documents from an es index"
  [{:keys [conn
           indexname
           mapping]}
   batch-size
   offset
   sort-order
   search_after]
  (let [sort-by (conj (case mapping
                        "event" [{"timestamp" sort-order}]
                        "identity" []
                        [{"modified" sort-order}])
                      {"_uid" sort-order})
        params
        (merge
         {:offset (or offset 0)
          :limit batch-size}
         (when sort-order
           {:sort sort-by})
         (when search_after
           {:search_after search_after}))]
    (es-doc/search-docs conn
                        indexname
                        mapping
                        nil
                        {}
                        params)))

(defn target-index-settings [settings]
  {:index (into settings
                {:number_of_replicas 0
                 :refresh_interval -1})})

(defn revert-optimizations-settings
  [settings]
  {:index (dissoc settings
                  :number_of_shards
                  :analysis)})

(defn create-target-store!
  "create the target store, pushing its template"
  [{:keys [conn indexname config] entity-type :type :as target-store}]
  (when (es-index/index-exists? conn indexname)
    (log/warnf "tried to create target store %s, but it already exists. Recreating it" indexname))
  (let [index-settings (target-index-settings (:settings config))]
    (log/infof "%s - purging indexes: %s" entity-type indexname)
    (es-index/delete! conn indexname)
    (log/infof "%s - creating index template: %s" entity-type indexname)
    (log/infof "%s - creating index: %s" entity-type indexname)
    (es-index/create-template! conn indexname config)
    (es-index/create! conn indexname index-settings)))

(s/defn init-migration :- MigrationSchema
  [migration-id :- s/Str
   prefix :- s/Str
   store-keys :- [s/Keyword]
   confirm? :- s/Bool]

  (let [source-stores (stores->maps (select-keys @stores store-keys))
        target-stores
        (source-store-maps->target-store-maps source-stores prefix)
        migration-properties (migration-store-properties)
        now (time/now)
        migration-stores (apply merge
                                (map (fn [[k v]]
                                       {k (init-migration-store v (k target-stores))})
                                     source-stores))
        migration {:id migration-id
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
   {source :source
    target :target :as raw-store} :- MigratedStore]
  (let [source-store (store->map (get @stores entity-type))
        target-store (update source-store :indexname (:index target))]
    (-> (assoc-in raw-store [:source :store] source-store)
        (assoc-in [:source :store] target-store))))

(s/defn with-search-after :- MigratedStore
  [raw-store :- MigratedStore]
  (let [target-store (get-in raw-store [:target :store])
        {:keys [data paging] :as res} (fetch-batch target-store 1 0 "desc" nil)]
    (assoc-in raw-store [:source :search_after] (:sort paging))))

(s/defn enrich-stores :- {s/Keyword MigratedStore}
  [stores :- {s/Keyword MigratedStore}]
  (->> (map (fn [[store-key raw]]
              {store-key (-> (with-store-map store-key raw)
                             with-search-after)})
            stores)
       (apply merge)))

(s/defn restart-migration :- MigrationSchema
  [migration-id :- s/Str
   es-conn :- ESConn]
  (let [{:keys [indexname entity]} (migration-store-properties)
        migration-raw (es-doc/get-doc es-conn indexname entity migration-id nil)]
    (when-not migration-raw
      (log/errorf "migration not found: %s" migration-id)
      (throw (ex-info "migration not found" {:id migration-id})))
    (->> (:store migration-raw)
         (map (fn [[k v]])))
    (update migration-raw :stores enrich-stores)))

(s/defn update-migration-store
  [migration-id :- s/Str
   store-key :- s/Keyword
   migrated-doc :- PartialMigratedStore
   es-conn :- ESConn]
  (let [partial-doc {:stores {store-key migrated-doc}}
        {:keys [indexname entity]} (migration-store-properties)]
    (es-doc/update-doc es-conn
                       indexname
                       (name entity)
                       migration-id
                       partial-doc
                       "true")))

(defn finalize-migration!
  [source-store target-store]
  (log/infof "%s - update index settings" (:type source-store))
  (es-index/update-settings! (:conn target-store)
                             (:indexname target-store)
                             (revert-optimizations-settings
                              (get-in target-store [:config :settings])))
  (log/infof "%s - trigger refresh" (:type source-store))
  (es-index/refresh! (:conn target-store)
                     (:indexname target-store)))

(defn setup!
  "init properties, start CTIA and its store service"
  []
  (log/info "starting CTIA Stores...")
  (init!)
  (log-properties)
  (init-store-service!)
  (->> (migration-store-properties)
       init-store-conn
       (reset! migration-conn-state)))
