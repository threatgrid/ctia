(ns ctia.task.migration.migrate-es-stores
  (:require [ductile.schemas :refer [ESConn ESQuery]]
            [puppetlabs.trapperkeeper.app :as app]
            [clj-momo.lib.time :as time]
            [cheshire.factory :as factory]
            [clojure.pprint :as pp]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [ctia.entity.entities :as entities]
            [ctia.entity.sighting.schemas :refer [StoredSighting]]
            [ctia.features-service :as features-svc]
            [ctia.init :as init]
            [ctia.properties :as p]
            [ctia.stores.es.crud :refer [coerce-to-fn]]
            [ctia.stores.es.store :refer [StoreMap]]
            [ctia.task.migration.migrations :refer [available-migrations]]
            [ctia.task.migration.store :as mst]
            [schema-tools.core :as st]
            [schema.core :as s])
  (:import java.lang.AssertionError
           (com.fasterxml.jackson.core StreamReadConstraints StreamReadConstraints$Builder)))

(set! *warn-on-reflection* true)

(def default-batch-size 100)

;; TODO def => defn
(def all-types
  (assoc (->> (vals (entities/all-entities))
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
   confirm? :- s/Bool
   services :- mst/MigrationStoreServices]
  (let [event-store (mst/get-source-store :event services)]
    (loop [search_after nil]
      (let [{:keys [data paging]} (mst/fetch-deletes event-store
                                                     store-keys
                                                     created
                                                     batch-size
                                                     search_after)
            {new-search-after :sort next :next} paging]
        (doseq [[entity-type entities] data]
          (log/infof "Handling %s deleted %s during migration"
                     (count entities)
                     (name entity-type))
          (when confirm?
            (mst/batch-delete (get-in stores [entity-type :target :store])
                              (map :id entities))))
        (when next
          (recur new-search-after))))))

(defn append-coerced
  [coercer coerced entity]
  (try
    (->> (coercer entity)
         (update coerced :data conj))
    (catch Exception _e
      (update coerced :errors conj entity))))

(defn list-coerce-fn
  [Model]
  (let [coercer (coerce-to-fn Model)]
    #(reduce (partial append-coerced coercer) {} %)))

(s/defschema BatchParams
  (st/optional-keys
   {:source-store StoreMap
    :target-store StoreMap
    :migrated-count s/Int
    :buffer-size s/Int
    :search_after [s/Any]
    :migrations s/Any
    :entity-type s/Keyword
    :batch-size s/Int
    :migration-id s/Str
    :list-coerce s/Any
    :migration-es-conn ESConn
    :confirm? s/Bool
    :documents [{s/Any s/Any}]
    :query ESQuery
    :jackson-config (s/maybe mst/JacksonConfig)}))

(s/defn read-source-batch :- (s/maybe BatchParams)
  "This function retrieves in `source-store` a batch of documents that match the given `query`.
   When not nil, the `search_after` parameter is used to skip previously retrieved data. The
   returned result prepares the next batch parameters with new `search_after` along with the
   documents that have to be written in target."
  [{:keys [source-store
           search_after
           batch-size
           query]
    :as batch-params} :- (s/maybe BatchParams)]
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

(s/defn read-source
  "returns a lazy-seq of batch from source store"
  [read-params :- (s/maybe BatchParams)]
  (iteration read-source-batch
             :initk read-params
             :kf #(dissoc % :documents)))

(s/defn write-target :- s/Int
  "This function writes a batch of documents which are (1) modified with `migrations` functions,
   (2) validated by `list-coerce` and (3) written into given `target-store`. This function updates
   the number of successfully migrated documents and search_after in the migration state identified
   by given`migration-id` It finally returns this new number of successfully migrated documents."
  [migrated-count :- s/Int
   {:keys [target-store
           documents
           search_after
           migrations
           entity-type
           batch-size
           migration-id
           list-coerce
           migration-es-conn
           confirm?]} :- BatchParams
   services :- mst/MigrationStoreServices]
  (let [migrated (transduce migrations conj documents)
        {:keys [data errors]} (list-coerce migrated)
        new-migrated-count (+ (count data) migrated-count)]
    (doseq [entity errors]
      (let [message
            (format "%s - Cannot migrate entity: %s"
                    (name entity-type)
                    (pr-str entity))]
        (log/error message)))
    (when confirm?
      (when (seq data) (mst/store-batch target-store data services))
      (mst/rollover target-store batch-size new-migrated-count services)
      (mst/update-migration-store migration-id
                                  entity-type
                                  (cond-> {:target {:migrated new-migrated-count}}
                                    search_after (assoc :source
                                                        {:search_after search_after}))
                                  migration-es-conn
                                  services))
    (log/infof "%s - migrated: %s documents"
               (name entity-type)
               new-migrated-count)
    new-migrated-count))

(s/defn migrate-query :- BatchParams
  "migrate documents that match given `query`"
  [{:keys [entity-type
           migrated-count]
    :as migration-params} :- BatchParams
   query :- ESQuery
   services :- mst/MigrationStoreServices]
  (log/infof "%s - handling sliced query %s"
             (name entity-type)
             (pr-str query))
  (let [read-params (assoc migration-params :query query)
        data (read-source read-params)
        new-migrated-count (reduce #(write-target %1 %2 services)
                                   migrated-count
                                   data)]
    (assoc migration-params
           :migrated-count
           new-migrated-count)))

(s/defn wrap-jackson-config [request-fn {:keys [maxNestingDepth maxNumberLength maxStringLength]} :- mst/JacksonConfig]
  (let [factory (doto (factory/make-json-factory factory/default-factory-options)
                  (.setStreamReadConstraints
                    (-> (StreamReadConstraints/builder)
                        (cond->
                          maxNestingDepth (StreamReadConstraints$Builder/.maxNestingDepth maxNestingDepth)
                          maxNumberLength (StreamReadConstraints$Builder/.maxNumberLength maxNumberLength)
                          maxStringLength (StreamReadConstraints$Builder/.maxStringLength maxStringLength))
                        .build)))]
    #(binding [factory/*json-factory* factory]
       (request-fn %))))

(s/defn migrate-store
  "migrate a single store"
  [migration-state
   entity-type
   migrations
   batch-size
   confirm?
   jackson-config
   services :- mst/MigrationStoreServices]
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
        source-store (cond-> source-store
                       jackson-config (update-in [:conn :request-fn] wrap-jackson-config jackson-config))
        base-params {:source-store source-store
                     :target-store target-store
                     :migrated-count migrated-count-state
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
                                  {:started (time/now)}
                                  services))
    (->> (reduce #(migrate-query %1 %2 services)
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
                               @mst/migration-es-conn
                               services))))

(s/defn migrate-store-indexes
  "migrate the selected es store indexes"
  [{:keys [migration-id
           migrations
           store-keys
           batch-size
           confirm?
           restart?
           jackson-config]
    :as migration-params} :- mst/MigrationParams
   services :- mst/MigrationStoreServices]
  (let [migration-state (if restart?
                          (mst/get-migration migration-id @mst/migration-es-conn services)
                          (mst/init-migration migration-params services))
        migrations (compose-migrations migrations)
        batch-size (or batch-size default-batch-size)]
    (log/infof "migrating stores: %s" store-keys)
    (log/infof "set batch size: %s" batch-size)
    (doseq [entity-type (keys (:stores migration-state))]
      (migrate-store migration-state
                     entity-type
                     migrations
                     batch-size
                     confirm?
                     jackson-config
                     services))
    (handle-deletes migration-state store-keys batch-size confirm? services)))

(defn exit [error?]
  (if error?
    (System/exit -1)
    (System/exit 0)))

(s/defn ^:always-validate check-migration-params
  [{:keys [prefix
           restart?
           store-keys
           migrations
           store] :as params} :- mst/MigrationParams
   get-in-config]
  (when-not restart?
    (assert prefix "Please provide an indexname prefix for target store creation")
    (assert (seq store-keys) "Please provide the store-keys for source store to migrate")
    (assert (seq migrations) "Please provide the migrations' ids to apply"))
  (doseq [store-key store-keys]
    (let [origin-store (get-in-config [:ctia :store :es store-key])
          default-target-store (get-in store [:es :default])
          target-store (get-in store [:es store-key])
          origin-indexname (:indexname origin-store)]
      (when (and (= (mst/prefixed-index origin-indexname prefix)
                    origin-indexname)
                 (nil? (:host default-target-store))
                 (nil? (:host target-store))
                 (nil? (:indexname target-store)))
        (throw (AssertionError.
                (format "The migration setup is misconfigured.\nThe source and target indices are identical: %s\n%s\n."
                        (pr-str params)
                        (pr-str origin-store)
                        (pr-str target-store)))))))
  true)

(s/defn prepare-params :- mst/MigrationParams
  [migration-properties]
  (let [string-to-coll #(map (comp keyword string/trim)
                             (string/split (or % "") #","))]
    (-> migration-properties
        (update :migrations string-to-coll)
        (update :store-keys string-to-coll))))

(s/defn run-migration
  [options]
  (log/info "migrating all ES Stores")
  (try
    (let [_ (log/info "starting CTIA Stores...")
          config (p/build-init-config)
          get-in-config (partial get-in config)
          app (init/start-ctia! {:services [features-svc/features-service]
                                 :config config})
          services (app/service-graph app)]
      (mst/setup! services)
      (doto (-> (get-in-config [:ctia :migration])
                (into options)
                prepare-params)
        (->> pr-str (log/info "migration started"))
        (check-migration-params get-in-config)
        (migrate-store-indexes services))
      (log/info "migration complete")
      (exit false))
    (catch AssertionError e
      (log/error (.getMessage e))
      (exit true))
    (catch Exception e
      (log/error e "Unexpected error during migration")
      (exit true))
    (finally
      (log/error "Unknown error")
      (exit true))))

(def cli-options
  ;; An option with a required argument
  [["-c" "--confirm" "really do the migration?"]
   ["-r" "--restart" "restart ongoing migration?"]
   [nil "--jackson-maxNestingDepth INT" "Sets the maximum nesting depth. Corresponds to jackson's StreamReadConstraints$Builder/.maxNestingDepth"
    :parse-fn Integer/parseInt]
   [nil "--jackson-maxStringLength INT" "Sets the maximum string length (in chars or bytes, depending on input context). Corresponds to jackson's StreamReadConstraints$Builder/.maxStringLength"
    :parse-fn Integer/parseInt]
   [nil "--jackson-maxNumberLength INT" "Sets the maximum number length (in chars or bytes, depending on input context). Corresponds to jackson's StreamReadConstraints$Builder/.maxNumberLength"
    :parse-fn Integer/parseInt]
   ["-h" "--help"]])

(s/defn extract-jackson-config :- (s/maybe mst/JacksonConfig)
  [options]
  (-> options
      (select-keys [:jackson-maxNestingDepth
                    :jackson-maxNumberLength
                    :jackson-maxStringLength])
      (update-keys #(keyword (subs (name %) (count "jackson-"))))
      not-empty))

(defn prep-run-migration-options [args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)
        {:keys [restart confirm]} options
        jackson-config (extract-jackson-config options)]
    (cond
      errors (binding [*out* *err*]
               (println (string/join "\n" errors))
               (println summary)
               1)
      (:help options) (do (println summary)
                          0)
      :else (do (pp/pprint options)
                (cond-> {:confirm? confirm
                         :restart? restart}
                  jackson-config (assoc :jackson-config jackson-config))))))

(defn -main [& args]
  (let [options-or-exit (prep-run-migration-options args)]
    (if (integer? options-or-exit)
      (System/exit options-or-exit)
      (run-migration options-or-exit))))
