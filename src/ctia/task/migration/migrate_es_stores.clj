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
            [clojure.core.async :as async :refer [chan <!! >!! <! close! go go-loop]]
            [schema.core :as s]))

(def default-batch-size 100)
(def default-buffer-size 30)

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
  "compose migration transformations from a list of keywords"
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
   batch-size :- s/Int
   confirm? :- s/Bool]
  (loop [search_after nil]
    (let [{:keys [data paging]} (mst/fetch-deletes store-keys created batch-size search_after)
          {new-search-after :sort next :next} paging]
      (doseq [[entity-type entities] data]
        (log/infof "Handling %s deleted %s during migration"
                   (count entities)
                   (name entity-type))
        (when confirm?
          (mst/batch-delete (get-in stores [entity-type :target :store])
                            (map :id entities))))
      (when next
        (recur new-search-after)))))

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
   buffer-size
   confirm?]
  (log/infof "migrating store: %s" entity-type)
  (let [{stores :stores migration-id :id} migration-state
        {:keys [source target started]} (get stores entity-type)
        {source-store :store
         search_after :search_after
         source-store-size :total} source
        {target-store :store
         migrated-count-state :migrated} target
        store-schema (type->schema (keyword (:type target-store)))
        list-coerce (list-coerce-fn store-schema)
        data-chan (chan buffer-size)
        batches (atom 0)]

    (log/infof "%s - store size: %s records"
               (:type source-store)
               source-store-size)

    (when (and confirm? (not started))
      (mst/update-migration-store migration-id
                                  entity-type
                                  {:started (time/now)}))

    (go-loop [current-search-after search_after]
      ;; async go loop that reads batches from source and produces them in chan
      (swap! batches inc)
      (let [{:keys [data paging]} (mst/fetch-batch source-store
                                                   batch-size
                                                   0
                                                   "asc"
                                                   current-search-after)
            next (:next paging)]
        (>!! data-chan data)
        (if next
          (recur  (:sort paging))
          (close! data-chan))))

    (loop [migrated-count migrated-count-state]
      ;; sync loop that consumes batches in chan and migrates them to target
      (let [documents (<!! (go (<! data-chan)))
            migrated (transduce migrations conj documents)
            {:keys [data errors]} (list-coerce migrated)
            new-migrated-count (+ migrated-count (count data))]
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
                                      {:target {:migrated new-migrated-count}}
                                      @mst/migration-es-conn))
        (log/infof "%s - migrated: %s documents"
                   (:type source-store)
                   new-migrated-count)
        (swap! batches dec)
        (if (< 0 @batches)
          (recur new-migrated-count)
          (do (log/infof "%s - finished migrating %s documents"
                         (:type source-store)
                         new-migrated-count)
              (when confirm?
                (mst/finalize-migration! migration-id
                                         entity-type
                                         source-store
                                         target-store
                                         @mst/migration-es-conn))))))))

(s/defn migrate-store-indexes
  "migrate the selected es store indexes"
  [migration-id :- s/Str
   prefix :- s/Str
   migrations :- [s/Keyword]
   store-keys :- [s/Keyword]
   batch-size :- s/Int
   buffer-size :- s/Int
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
      (migrate-store migration-state
                     entity-type
                     migrations
                     batch-size
                     buffer-size
                     confirm?))
    (handle-deletes migration-state store-keys batch-size confirm?)))

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
  (assert migration-id "Please provide an unique ID for this migration process")
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
  [["-i" "--id ID" "The ID of the migration state to create or restar"
    :default (str "migration-" (java.util.UUID/randomUUID))]
   ["-p" "--prefix PREFIX" "prefix of the newly created indices"]
   ["-m" "--migrations MIGRATIONS" "a comma separated list of migration ids to apply"
    :parse-fn #(map keyword (string/split % #","))]
   ["-b" "--batch-size SIZE" "number of migrated documents per batch"
    :default default-batch-size
    :parse-fn read-string
    :validate [#(< 0 %) "batch-size must be a positive number"]]
   ["" "--buffer-size SIZE" "max number of batches in buffer between source and target"
    :default default-buffer-size
    :parse-fn read-string
    :validate [#(< 0 %) "buffer-size must be a positive number"]]
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
                buffer-size
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
                   buffer-size
                   confirm
                   restart)))
