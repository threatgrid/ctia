(ns ctia.task.migration.store
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [schema-tools.core :as st]
            [clj-momo.lib.time :as time]
            [clj-momo.lib.es
             [schemas :refer [ESConn]]
             [conn :as conn]
             [document :as es-doc]
             [index :as es-index]]
            [ctia.stores.es
             [init :refer [init-store-conn init-es-conn! get-store-properties]]
             [mapping :as em]
             [store :refer [StoreMap store->map]]]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :refer [properties]]
             [store :refer [stores]]]))

(def timeout (* 5 60000))

(def token-mapping
  (dissoc em/token :fielddata))

(def migration-mappings
  {"migration"
   {:dynamic false
    :properties
    {:id token-mapping
     :timestamp em/ts
     :stores {:type "nested"
              :properties {:type token-mapping
                           :source {:type "object"
                                    :properties
                                    {:index token-mapping
                                     :total {:type "long"}}}
                           :target {:type "object"
                                    :properties
                                    {:index token-mapping
                                     :migrated {:type "long"}}}
                           :started em/ts
                           :completed em/ts}}}}})

(defn migration-store-properties
  []
  (-> (get-store-properties :migration)
      (merge
       {:shards 1
        :replicas 1
        :refresh true
        :mappings migration-mappings})))

(s/defschema MigratedStore
  {:type s/Keyword
   :source {:index s/Str
            :total s/Int
            :store StoreMap}
   :target {:index s/Str
            :migrated s/Int
            :store StoreMap}
   (s/optional-key :started) s/Inst
   (s/optional-key :completed) s/Inst})

(s/defschema MigrationSchema
  {:id s/Str
   :created java.util.Date
   :stores [MigratedStore]})

(defn store-size
  [{:keys [conn indexname mapping]}]
  (es-doc/count-docs conn indexname mapping))

(defn init-migration-store
  [entity-type source target]
  {:type entity-type
   :source {:index (:indexname source)
            :total (or (store-size source) 0)
            :store source}
   :target {:index (:indexname target)
            :migrated 0
            :store target}})

(s/defn wo-storemaps
  [{:keys [stores] :as migration} :- MigrationSchema]
  (assoc migration
         :stores
         (map #(-> (update-in % [:source] dissoc :store)
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

(defn stores->maps
  "transform store records to maps"
  [stores]
  (into {}
        (map (fn [[store-key store-record]]
               {store-key
                (store->map store-record
                            {:cm (conn/make-connection-manager
                                  {:timeout timeout})})})
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

(s/defn init-migration :- MigrationSchema
  [id :- (s/maybe s/Str)
   prefix :- s/Str
   store-keys :- [s/Keyword]]

  (let [source-stores (stores->maps (select-keys @stores store-keys))
        target-stores
        (source-store-maps->target-store-maps source-stores
                                              prefix)
        migration-properties (migration-store-properties)
        now (time/now)
        migration-id (or id (str "migration-" (str (java.util.UUID/randomUUID))))
        migration-stores (map (fn [[k v]]
                                (init-migration-store k v (k target-stores)))
                              source-stores)
        migration {:id migration-id
                   :created now
                   :stores migration-stores}
        es-conn-state (init-es-conn! migration-properties)]
    (store-migration migration (:conn es-conn-state))
    migration))

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
  [target-store confirm? restart?]

  (when (and confirm?
             ;; do not recreate index on restart
             (not (and (es-index/index-exists? (:conn target-store)
                                               (:indexname target-store))
                       restart?)))
    (let [wildcard (:indexname target-store)
          settings (get-in target-store [:config :settings])
          index-settings (target-index-settings settings)]
      (log/infof "%s - purging indexes: %s"
                 (:type target-store)
                 wildcard)
      (es-index/delete!
       (:conn target-store)
       wildcard)
      (log/infof "%s - creating index template: %s"
                 (:type target-store)
                 (:indexname target-store))
      (log/infof "%s - creating index: %s"
                 (:type target-store)
                 (:indexname target-store))

      (es-index/create-template!
       (:conn target-store)
       (:indexname target-store)
       (:config target-store))

      (es-index/create!
       (:conn target-store)
       (:indexname target-store)
       index-settings))))

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
