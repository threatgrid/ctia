(ns ctia.task.migration.migrate-es-stores
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :refer [init!]]
             [store :refer [stores]]]
            [ctia.entity.entities :refer [entities]]
            [ctia.entity.sighting.schemas :refer [StoredSighting]]
            [ctia.stores.es.crud :refer [coerce-to-fn]]
            [ctia.task.migration.migrations :refer [available-migrations]]
            [ctia.task.migration.store :as mst]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def default-batch-size 100)

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
  (init!)
  (log-properties)
  (init-store-service!))

(defn restart-params
  [target-store]
  (let [{:keys [data paging] :as res} (mst/fetch-batch target-store 1 0 "desc" nil)]
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
  (mst/create-target-store! target-store confirm? restart?)
  (let [{:keys [search_after migrated-count]
         :or {search_after nil
              migrated-count 0}} (when restart?
                                   (restart-params target-store))
        current-store-size (mst/store-size current-store)
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
            (mst/fetch-batch current-store
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
            (mst/store-batch target-store migrated))

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
              (mst/finalize-migration! current-store target-store)
))))))

(defn migrate-store-indexes
  "migrate all es store indexes"
  [prefix migrations store-keys batch-size confirm? restart?]
  (let [current-stores (mst/stores->maps (select-keys @stores store-keys))
        target-stores
        (mst/source-store-maps->target-store-maps current-stores
                                              prefix)
        migrations (compose-migrations migrations)
        batch-size (or batch-size default-batch-size)]

    (log/infof "migrating stores: %s" (keys current-stores))
    (log/infof "set batch size: %s" batch-size)

    (mst/init-migration "test" current-stores target-stores)
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
        target-store-size (mst/store-size target-store)]
    (log/infof "%s - store size: %s records"
               (:type target-store)
               target-store-size)

    (loop [offset 0
           sort-keys nil
           checked-count 0]
      (let [{:keys [data paging]
             :as batch}
            (mst/fetch-batch target-store
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
  (let [current-stores (mst/stores->maps (select-keys @stores store-keys))
        target-stores
        (mst/source-store-maps->target-store-maps current-stores
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
