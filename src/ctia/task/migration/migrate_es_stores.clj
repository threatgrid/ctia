(ns ctia.task.migration.migrate-es-stores
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]

            [schema-tools.core :as st]
            [schema.core :as s]
            [clj-momo.lib.time :as time]

            [ctia.store :refer [stores]]
            [ctia.entity.entities :refer [entities]]
            [ctia.entity.sighting.schemas :refer [StoredSighting]]
            [ctia.stores.es.crud :refer [coerce-to-fn]]
            [ctia.task.migration.migrations :refer [available-migrations]]
            [ctia.task.migration.store :as mst]))

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

(defn read-source
  "This function retrieves in `source-store` all the documents that match the given `query`
   by batch of maximum size `batch-size`. When not nil the `search_after` parameter is used
   to skip previously retrieved data. Then it pushes the retrieved data in the given channel,
  `data-chan`, along with next search_after value for eventual further restart. Once all the
   documents that match given query are retrieved, it closes the channel `data-chan`"
  [{:keys [source-store
           search_after
           batch-size
           query]
    :as batch-params}]
    ;; loop that reads batches from source and produces them in chan
  (when batch-params
    (let [{:keys [data paging]} (mst/query-fetch-batch query
                                                       source-store
                                                       batch-size
                                                       0
                                                       "asc"
                                                       search_after)
          next-search-after (:sort paging)]
      (when (seq data)
        (assoc batch-params
               :documents data
               :search_after next-search-after)))))

(defn write-target
  "This function reads batches from `data-chan` (pushed by read-source) until it reads
   a `nil`, meaning that the channel has been closed by `read-source` function. The
   batches are (1) modified with `migrations` functions, (2) validated by `list-coerce`
   and (3) written into given `target-store`. At each batch the migration state, number
   of migrated documents and search_after value, is updated in ES after the documents
   are written in the store. If the process fails or is stopped before that update of
   the migration state, the documents will be red again by `read-source` and overridden
   in case of restart."
  [migrated-count
   {:keys [target-store
           documents
           search_after
           migrations
           entity-type
           batch-size
           migration-id
           list-coerce
           migration-es-conn
           confirm?]
    :as batch-params}]
  (let [migrated (transduce migrations conj documents)
        {:keys [data errors]} (list-coerce migrated)
        new-migrated-count (+ migrated-count (count data))]
    (doseq [entity errors]
      (let [message
            (format "%s - Cannot migrate entity: %s"
                    (name entity-type)
                    (pr-str entity))]
        (log/error message)))
    (when confirm?
      (when (seq data) (mst/store-batch target-store data))
      (mst/rollover target-store batch-size new-migrated-count)
      (mst/update-migration-store migration-id
                                  entity-type
                                  (into {:target {:migrated new-migrated-count}}
                                        (when search_after
                                          {:source {:search_after search_after}}))
                                  migration-es-conn))
    (log/infof "%s - migrated: %s documents"
               (name entity-type)
               new-migrated-count)
    new-migrated-count))

(defn migrate-query
  [{:keys [entity-type
           migrated-count
           buffer-size]
    :as migration-params}
   query]
  (log/infof "%s - handling sliced query %s"
             (name entity-type)
             (pr-str query))
  (let [read-params (assoc migration-params :query query)
        data-queue (->> (iterate read-source read-params)
                        rest
                        (seque buffer-size))
        new-migrated-count (reduce write-target
                                   migrated-count
                                   (take-while seq data-queue))]
    (assoc migration-params
           :migrated-count
           new-migrated-count)))

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
        queries (mst/sliced-queries source-store search_after "week")
        base-params {:source-store source-store
                     :target-store target-store
                     :migrated-count migrated-count-state
                     :buffer-size buffer-size
                     :search_after search_after
                     :migrations migrations
                     :entity-type entity-type
                     :batch-size batch-size
                     :migration-id migration-id
                     :list-coerce list-coerce
                     :migration-es-conn @mst/migration-es-conn
                     :confirm? confirm?}]
    (log/infof "%s - store size: %s records"
               (:type source-store)
               source-store-size)
    (when (and confirm? (not started))
      (mst/update-migration-store migration-id
                                  entity-type
                                  {:started (time/now)}))
    (->> (reduce migrate-query
                 base-params
                 queries)
         :migrated-count
         (log/infof "%s - finished migrating %s documents"
                    (name entity-type)))
    (when confirm?
      (mst/finalize-migration! migration-id
                               entity-type
                               source-store
                               target-store
                               @mst/migration-es-conn))))

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

(defn exit [error?]
  (if error?
    (System/exit -1)
    (System/exit 0)))

(defn run-migration
  [migration-id prefix migrations store-keys batch-size buffer-size confirm? restart?]
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
                           buffer-size
                           confirm?
                           restart?)
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
