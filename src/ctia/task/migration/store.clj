(ns ctia.task.migration.store
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [clj-momo.lib.time :as time]
            [clj-momo.lib.es
             [conn :as conn]
             [document :as es-doc]
             [index :as es-index]]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [store->map]]]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :refer [properties]]
             [store :refer [stores]]]))

(def timeout (* 5 60000))

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

(def migration-store
  {:entity :migration
   :indexname (get-in @properties [:ctia :migration :es :indexname])
   :shards 1
   :replicas 1})

(s/defschema MigratedStore
  {:source {:index s/Str
            :total s/Int}
   :target {:index s/Str
            :migrated s/Int}
   (s/optional-key :started) s/Inst
   (s/optional-key :completed) s/Inst})

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
            :total (or (store-size source) 0)}
   :target {:index (:indexname target)
            :migrated 0}})

(s/defn init-migration :- MigrationSchema
  [id :- s/Str
   source-stores
   target-stores]
  (let [now (time/now)
        migration-id (or id (str "migration-" (str (java.util.UUID/randomUUID))))
        migration-stores (map (fn [[k v]]
                                {k (init-migration-store v (k target-stores))})
                              source-stores)
        migration {:id migration-id
                   :created now
                   :stores (apply merge migration-stores)}]
    migration))

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
  [current-stores prefix]
  (into {}
        (map (fn [[sk sr]]
               {sk (source-store-map->target-store-map sr prefix)})
             current-stores)))

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
