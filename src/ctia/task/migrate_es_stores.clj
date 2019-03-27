(ns ctia.task.migrate-es-stores
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clj-momo.lib.time :as time]
            [clj-momo.lib.es
             [conn :as conn]
             [document :as es-doc]
             [index :as es-index]]
            [ctia.lib.collection :refer [fmap]]
            [ctia.stores.es.store :refer [store->map]]
            [ctia.stores.es.crud :refer [coerce-to-fn]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ctia.properties :refer [properties]]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :as p :refer [properties]]
             [store :refer [stores]]]
            [ctia.entity.entities :refer [entities]]
            [ctia.entity.sighting.schemas :refer [StoredSighting]]
            [ctia.stores.es.crud :refer [coerce-to-fn]]
            [ctia.task.migrations :refer [available-migrations]]
            [schema-tools.core :as st]
            [ctia.stores.es.mapping :as em]
            [schema.core :as s]))

(def default-batch-size 100)
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

(defn optimizations-enabled? []
  (get-in @properties
          [:ctia
           :migration
           :optimizations]))

(def all-types
  (assoc (apply merge {}
                (map (fn [[_ {:keys [entity stored-schema]}]]
                       {entity stored-schema}) entities))
         :sighting (st/merge StoredSighting
                             {(s/optional-key :observables_hash) s/Any})))

(defn type->schema [type]
  (if-let [schema (get all-types type)]
    schema
    (do (log/warnf "missing schema definition for: %s" type)
        s/Any)))

(defn compose-migrations
  "compose migrations from a list of keywords"
  [migration-keys]
  (let [migrations
        (vals (select-keys available-migrations
                           migration-keys))]
    (if (seq migrations)
      (apply comp migrations)
      (do (log/warn "target migration not found, copying data")
          (map identity)))))

(defn setup
  "init properties, start CTIA and its store service"
  []
  (log/info "starting CTIA Stores...")
  (p/init!)
  (log-properties)
  (init-store-service!))

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

(def optimized-settings
  {:index
   {:number_of_replicas 0
    :refresh_interval -1}})

(defn make-target-index-settings [settings]
  (if (optimizations-enabled?)
    (merge
     {:index settings}
     optimized-settings)
    settings))

(defn create-target-store
  "create the target store, pushing its template"
  [target-store]
  (let [wildcard (:indexname target-store)
        settings (get-in target-store [:config :settings])
        index-settings (make-target-index-settings
                        settings)]
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
     index-settings)))

(defn revert-optimizations-settings
  [settings]
  {:index (dissoc settings
                  :number_of_shards
                  :analysis)})

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

(defn restart-params
  [target-store]
  (let [{:keys [data paging] :as res} (fetch-batch target-store 1 0 "desc" nil)]
    {:search_after (:sort paging)
     :migration-count (:total-hits paging)}))

(defn migrate-store
  "migrate a single store"
  [current-store
   target-store
   migrations
   batch-size
   confirm?
   restart?]
  (when (and confirm?
             ;; do not recreate index on restart
             (not (and (es-index/index-exists? (:conn target-store)
                                               (:indexname target-store))
                       restart?)))
    (create-target-store target-store))
  (let [{:keys [search_after migrated-count]
         :or {search_after nil
              migrated-count 0}} (when restart?
                                   (restart-params target-store))
        current-store-size (store-size current-store)
        store-schema (type->schema (keyword (:type target-store)))
        coerce! (coerce-to-fn [store-schema])]

    (log/infof "%s - store size: %s records"
               (:type current-store)
               current-store-size)

    (loop [offset 0
           search_after search_after
           migrated-count migrated-count]
      (let [{:keys [data paging]
             :as batch}
            (fetch-batch current-store
                         batch-size
                         offset
                         "asc"
                         search_after)
            next (:next paging)
            offset (:offset next 0)
            search_after (:sort paging)
            migrated (transduce migrations conj data)
            migrated-count (+ migrated-count
                              (count migrated))]

        (when (seq migrated)
          (try (coerce! migrated)
               (catch Exception e
                 (if-let [errors (some->> (ex-data e) :error (remove nil?))]
                   (let [message
                         (format (str "%s - Invalid batch, certainly a coercion issue "
                                      "errors: %s")
                                 (pr-str (:type current-store))
                                 (pr-str errors))]
                     (log/error message)
                     message)
                   (throw e))))

          (when confirm?
            (store-batch target-store migrated))

          (log/infof "%s - migrated: %s documents"
                     (:type current-store)
                     migrated-count))
        (if next
          (recur offset
                 search_after
                 migrated-count)
          (do (log/infof "%s - finished migrating %s documents"
                         (:type current-store)
                         migrated-count)

              (when (optimizations-enabled?)
                (log/infof "%s - update index settings" (:type current-store))
                (es-index/update-settings! (:conn target-store)
                                           (:indexname target-store)
                                           (revert-optimizations-settings
                                            (get-in target-store [:config :settings])))

                (log/infof "%s - trigger refresh" (:type current-store))
                (es-index/refresh! (:conn target-store)
                                   (:indexname target-store)))))))))

(defn migrate-store-indexes
  "migrate all es store indexes"
  [prefix migrations store-keys batch-size confirm? restart?]
  (let [current-stores (stores->maps (select-keys @stores store-keys))
        target-stores
        (source-store-maps->target-store-maps current-stores
                                              prefix)
        migrations (compose-migrations migrations)
        batch-size (or batch-size default-batch-size)]

    (log/infof "migrating stores: %s" (keys current-stores))
    (log/infof "set batch size: %s" batch-size)

    (clojure.pprint/pprint (init-migration "test" current-stores target-stores))
    (doseq [[sk sr] current-stores]
      (log/infof "migrating store: %s" sk)
      (migrate-store sr
                     (sk target-stores)
                     migrations
                     batch-size
                     confirm?
                     restart?))))

(defn check-store
  "check a single store"
  [target-store
   batch-size]
  (let [store-schema (type->schema (keyword (:type target-store)))
        coerce! (coerce-to-fn [store-schema])
        target-store-size (store-size store-size)]
    (log/infof "%s - store size: %s records"
               (:type target-store)
               target-store-size)

    (loop [offset 0
           sort-keys nil
           checked-count 0]
      (let [{:keys [data paging]
             :as batch}
            (fetch-batch target-store
                         batch-size
                         offset
                         nil
                         sort-keys)
            next (:next paging)
            offset (:offset next 0)
            search_after (:sort paging)
            checked (coerce! data)
            checked-count (+ checked-count
                             (count checked))]
        (if next
          (recur offset search_after checked-count)
          (log/infof "%s - finished checking %s documents"
                     (:type target-store)
                     checked-count))))))

(defn check-store-index
  [[sk sr :as store] batch-size]
  (try
    (log/infof "checking store: %s" sk)
    (check-store sr batch-size)
    (catch Exception e
      (if-let [errors (some->> (ex-data e) :error (remove nil?))]
        (let [message
              (format (str "The store %s is invalid, certainly a coercion issue "
                           "errors: %s")
                      sk
                      (pr-str errors))]
          (log/error message)
          message)
        (throw e)))))

(defn check-store-indexes
  "check all new es store indexes"
  [store-keys batch-size prefix]
  (let [current-stores (stores->maps (select-keys @stores store-keys))
        target-stores
        (source-store-maps->target-store-maps current-stores
                                              prefix)
        batch-size (or batch-size default-batch-size)]

    (log/infof "checking stores: %s" (keys current-stores))
    (log/infof "set batch size: %s" batch-size)
    (keep #(check-store-index % batch-size) target-stores)))

(defn exit [error?]
  (if error?
    (System/exit -1)
    (System/exit 0)))



(defn run-migration
  [prefix migrations store-keys batch-size confirm? restart?]
  (assert prefix "Please provide an indexname prefix for target store creation")
  (assert migrations "Please provide a csv migration list argument")
  (assert batch-size "Please specify a batch size")
  (log/info "migrating all ES Stores")
  (try
    (setup)
    (log/infof "optimizations enabled: %s"
               (optimizations-enabled?))
    (migrate-store-indexes prefix
                           migrations
                           store-keys
                           batch-size
                           confirm?
                           restart?)
    (when confirm?
      (when-let [errors (seq (check-store-indexes store-keys batch-size prefix))]
        (log/errorf "Schema errors during migration: %s"
                    (pr-str errors))
        (exit true)))
    (log/info "migration complete")
    (catch Exception e
      (log/error e "Unexpected error during migration")
      (exit true)))
  (exit false))

(def cli-options
  ;; An option with a required argument
  [["-p" "--prefix PREFIX" "prefix of the target size"
    :required true
]
   ["-m" "--migrations MIGRATIONS" "a comma separated list of migration ids to apply"
    :parse-fn #(map keyword (string/split % #","))]
   ["-b" "--batch-size SIZE" "migVration batch size"
    :default default-batch-size
    :parse-fn read-string
    :validate [#(< 0 %) "batch-size must be a positive number"]]
   ["-s" "--stores STORES" "comma separated list of stores to migrate"
    :default (-> (keys @stores) set (disj :identity))
    :parse-fn #(map keyword (string/split % #","))]
   ["-c" "--confirm" "really do the migration?"]
   ["-r" "--restart" "restart ongoing migration?"]
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]
    (when errors
      (binding  [*out* *err*]
        (println (clojure.string/join "\n" errors))
        (println summary))
      (System/exit 1))
    (when (:help options)
      (println summary)
      (System/exit 0))
    (clojure.pprint/pprint options)
    ;;(clojure.pprint/pprint migration-mapping)
    (run-migration (:prefix options)
                   (:migrations options)
                   (:stores options)
                   (:batch-size options)
                   (:confirm options)
                   (:restart options))))
