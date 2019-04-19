(ns ctia.task.migration.migrate-es-stores
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clj-momo.lib.time :as time]
            [ctia
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
  (assoc (->> (vals entities)
              (map (fn [{:keys [entity stored-schema]}]
                     {entity stored-schema}))
              (into {}))
         :sighting (st/merge StoredSighting
                             {(s/optional-key :observables_hash)
                              s/Any})))

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

(s/defn handle-deletes
  [{:keys [created stores]} :- mst/MigrationSchema
   store-keys :- [s/Keyword]
   confirm? :- s/Bool]
  (let [deletes (mst/fetch-deletes store-keys created)]
    (doseq [[entity-type entities] deletes]
      (log/infof "Handling %s deleted %s during migration"
                 (count entities)
                 (name entity-type))
      (when confirm?
        (mst/batch-delete (get-in stores [entity-type :target :store])
                          (map :id entities))))))

(defn append-coerced
  [coercer coerced entity]
  (try
    (->> (coercer entity)
         (update coerced :data conj))
    (catch Exception e
      (update coerced :errors conj entity))))

(defn list-coerce-fn
  [Model]
  (let [coercer (coerce-to-fn Model)]
    #(reduce (partial append-coerced coercer) {} %)))

(defn migrate-store
  "migrate a single store"
  [migration-state
   entity-type
   migrations
   batch-size
   confirm?]
  (let [{stores :stores migration-id :id} migration-state
        {:keys [source target started]} (get stores entity-type)
        {source-store :store
         search_after :search_after
         source-store-size :total} source
        {target-store :store
         migrated-count :migrated} target
        store-schema (type->schema (keyword (:type target-store)))
        list-coerce (list-coerce-fn store-schema)]

    (log/infof "%s - store size: %s records"
               (:type source-store)
               source-store-size)

    (loop [search_after search_after
           migrated-count migrated-count]
      (let [{:keys [data paging]} (mst/fetch-batch source-store
                                                   batch-size
                                                   0
                                                   "asc"
                                                   search_after)
            next (:next paging)
            search_after (:sort paging)
            migrated (transduce migrations conj data)
            {:keys [data errors]} (list-coerce migrated)
            migrated-count (+ migrated-count (count data))]
        (when (and confirm? (not started))
          (mst/update-migration-store migration-id
                                      entity-type
                                      {:started (time/now)}
                                      @mst/migration-es-conn))
        (doseq [entity errors]
          (let [message
                (format "%s - Cannot migrate entity: %s"
                        (:type source-store)
                        (pr-str entity))]
            (log/error message)))
        (when confirm?
          (when (seq data) (mst/store-batch target-store data))
          (mst/update-migration-store migration-id
                                      entity-type
                                      {:target {:migrated migrated-count}}
                                      @mst/migration-es-conn))

        (log/infof "%s - migrated: %s documents"
                   (:type source-store)
                   migrated-count)
        (if next
          (recur search_after migrated-count)
          (do (log/infof "%s - finished migrating %s documents"
                         (:type source-store)
                         migrated-count)
              (when confirm?
                (mst/finalize-migration! migration-id
                                         entity-type
                                         source-store
                                         target-store
                                         @mst/migration-es-conn))))))))

(s/defn migrate-store-indexes
  "migrate all es store indexes"
  [migration-id :- s/Str
   prefix :- s/Str
   migrations :- [s/Any]
   store-keys :- [s/Keyword]
   batch-size :- s/Int
   confirm? :- s/Bool
   restart? :- s/Bool]
  (let [migration-state (if restart?
                          (mst/get-migration migration-id @mst/migration-es-conn)
                          (mst/init-migration migration-id prefix store-keys confirm?))
        migrations (compose-migrations migrations)
        batch-size (or batch-size default-batch-size)]

    (log/infof "migrating stores: %s" store-keys)
    (log/infof "set batch size: %s" batch-size)

    (doseq [entity-type (keys (:stores migration-state))]
      (log/infof "migrating store: %s" entity-type)
      (migrate-store migration-state
                     entity-type
                     migrations
                     batch-size
                     confirm?))
    (handle-deletes migration-state store-keys confirm?)))

(defn check-store
  "check a single store"
  [target-store batch-size]
  (let [store-schema (type->schema (keyword (:type target-store)))
        list-coerce (list-coerce-fn store-schema)
        target-store-size (mst/store-size target-store)]
    (log/infof "%s - store size: %s records"
               (:type target-store)
               target-store-size)

    (loop [offset 0
           sort-keys nil
           checked-count 0
           invalids []]
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
            {:keys [errors]} (list-coerce data)
            checked-count (+ checked-count
                             (count data))]
        (if next
          (recur offset search_after checked-count (concat invalids errors))
          (do
            (log/infof "%s - finished checking %s documents"
                       (:type target-store)
                       checked-count)
            (when (seq errors)
              (log/warnf "%s - errors were detected: %s"
                         (:type target-store)
                         (pr-str errors)))))))))

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
    (doseq [[sk sr] target-stores]
      (log/infof "checking store: %s" sk)
      (check-store sr batch-size))))

(defn exit [error?]
  (if error?
    (System/exit -1)
    (System/exit 0)))

(defn run-migration
  [migration-id prefix migrations store-keys batch-size confirm? restart?]
  (assert migration-id "Please provide an indexname prefix for target store creation")
  (when-not restart?
    (assert prefix "Please provide an indexname prefix for target store creation"))
  (assert batch-size "Please specify a batch size")
  (assert migrations "Please provide a csv migration list argument")
  (log/info "migrating all ES Stores")
  (try
    (mst/setup!)
    (migrate-store-indexes migration-id
                           prefix
                           migrations
                           store-keys
                           batch-size
                           confirm?
                           restart?)
    (when confirm?
      (check-store-indexes store-keys batch-size prefix)
      (exit true))
    (log/info "migration complete")
    (catch Exception e
      (log/error e "Unexpected error during migration")
      (exit true)))
  (exit false))

(def cli-options
  ;; An option with a required argument
  [["-i" "--id ID" "The idID of the migration state to create or restar"
    :default (str "migration-" (java.util.UUID/randomUUID))]
   ["-p" "--prefix PREFIX" "prefix of the target size"]
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
  (let [{:keys [options errors summary]} (parse-opts args cli-options)
        {:keys [id
                prefix
                migrations
                stores
                batch-size
                confirm
                restart]} options]
    (when errors
      (binding  [*out* *err*]
        (println (clojure.string/join "\n" errors))
        (println summary))
      (System/exit 1))
    (when (:help options)
      (println summary)
      (System/exit 0))
    (clojure.pprint/pprint options)
    (run-migration id
                   prefix
                   migrations
                   stores
                   batch-size
                   confirm
                   restart)))
