(ns ctia.bundle.core
  (:require
   [clj-momo.lib.map :refer [deep-merge-with]]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [ctia.auth :as auth]
   [ctia.bulk.core :as bulk]
   [ctia.bundle.schemas :refer
    [BundleImportData BundleImportResult
     EntityImportData FindByExternalIdsServices]]
   [ctia.domain.entities :as ent :refer [with-long-id]]
   [ctia.lib.collection :as coll :refer [fmap]]
   [ctia.properties :as p]
   [ctia.schemas.core :as schemas :refer
    [APIHandlerServices HTTPShowServices NewBundle TempIDs]]
   [ctia.schemas.services :as external-svc-fns]
   [ctia.schemas.utils :as csu]
   [ctia.store :as store]
   [ctia.store-service.schemas :refer [GetStoreFn]]
   [ctim.domain.id :as id]
   [schema.core :as s])
  (:import (java.util UUID)
           (java.time Instant)))

(def find-by-external-ids-limit 200)

(def bundle-entity-keys
  (set (vals bulk/bulk-entity-mapping)))

(defn debug [msg v]
  (log/debug msg v)
  v)

(s/defn prefixed-external-ids :- [s/Str]
  "Returns all external IDs prefixed by the given key-prefix."
  [key-prefix external-ids]
  (filter #(string/starts-with? % key-prefix) external-ids))

(s/defn filter-external-ids :- [s/Str]
  "Returns the external IDs that can be used to check whether an entity has
  already been imported or not."
  [external-ids key-prefixes]
  (let [valid-ext-ids (if (seq key-prefixes)
                        (mapcat #(prefixed-external-ids % external-ids)
                                key-prefixes)
                        external-ids)]
    (when (> (count valid-ext-ids) 1)
      (log/warnf (str "More than 1 valid external ID has been found "
                      "(key-prefixes:%s | external-ids:%s)")
                 (pr-str key-prefixes) (pr-str external-ids)))
    valid-ext-ids))

(s/defn parse-key-prefixes :- [s/Str]
  "Parses a comma separated list of external ID prefixes"
  [s :- (s/maybe s/Str)]
  (when s
    (map string/trim
         (string/split s #","))))

(s/defn entity->import-data :- EntityImportData
  "Creates import data related to an entity"
  [{:keys [id external_ids] :as entity}
   entity-type
   external-key-prefixes]
  (let [key-prefixes (parse-key-prefixes external-key-prefixes)
        filtered-ext-ids (filter-external-ids external_ids key-prefixes)]
    (when-not (seq filtered-ext-ids)
      (log/warnf "No valid external ID has been provided (id:%s)" id))
    (cond-> {:new-entity entity
             :type entity-type}
      (schemas/transient-id? id) (assoc :original_id id)
      (seq filtered-ext-ids) (assoc :external_ids filtered-ext-ids))))

(s/defn all-pages
  "Retrieves all external ids using pagination."
  [entity-type
   external-ids
   auth-identity
   get-store :- GetStoreFn]
  (loop [ext-ids external-ids
         entities []]
    (let [query {:all-of {:external_ids ext-ids}}
          paging {:limit find-by-external-ids-limit}
          {results :data
           {next-page :next} :paging} (-> (get-store entity-type)
                                          (store/list-records query (auth/ident->map auth-identity) paging))
          acc-entities (into entities results)
          matched-ext-ids (into #{} (mapcat :external_ids results))
          remaining-ext-ids (remove matched-ext-ids ext-ids)]
      (if next-page
        (recur remaining-ext-ids acc-entities)
        acc-entities))))

(s/defn find-by-external-ids
  [import-data entity-type auth-identity
   {{:keys [get-store]} :StoreService} :- FindByExternalIdsServices]
  (let [external-ids (mapcat :external_ids import-data)]
    (log/debugf "Searching %s matching these external_ids %s"
                entity-type
                (pr-str external-ids))
    (if (seq external-ids)
      (debug (format "Results for %s:" (pr-str external-ids))
             (all-pages entity-type external-ids auth-identity get-store))
      [])))

(defn by-external-id
  "Index entities by external_id

   Ex:
   {{:external_id \"ctia-1\"} {:external_id \"ctia-1\"
                               :entity {...}}
    {:external_id \"ctia-2\"} {:external_id \"ctia-2\"
                               :entity {...}}}"
  [entities]
  (let [entity-with-external-id
        (->> entities
             (map (fn [{:keys [external_ids] :as entity}]
                    (set (map (fn [external_id]
                                {:external_id external_id
                                 :entity entity})
                              external_ids))))
             (apply set/union))]
    (set/index entity-with-external-id [:external_id])))

(s/defn entities-import-data->tempids :- TempIDs
  "Get a mapping table between orignal IDs and real IDs"
  [import-data :- [EntityImportData]]
  (->> import-data
       (filter #(and (:original_id %)
                     (:id %)))
       (map (fn [{:keys [original_id id]}]
              [original_id id]))
       (into {})))

(defn map-kv
  "Returns a map where values are the result of applying
   f to each key and value."
  {:example '((map-kv
               #(str (name %1) "-" %2)
               {:foo 3 :bar 4}) ;; => {:foo "foo-3", :bar "bar-4"}
              )}
  [f m]
  (into {}
        (map (fn [[k v]]
               [k (f k v)])
             m)))

(s/defn init-import-data :- [EntityImportData]
  "Create import data for a type of entities"
  [entities
   entity-type
   external-key-prefixes]
  (map #(entity->import-data % entity-type external-key-prefixes)
       entities))

(s/defn with-existing-entity :- EntityImportData
  "If the entity has already been imported, update the import data
   with its ID. If more than one old entity is linked to an external id,
   an error is reported."
  [{:keys [external_ids]
    :as entity-data} :- EntityImportData
   find-by-external-id :- (s/=> s/Any (s/named s/Any 'external_id))
   services :- HTTPShowServices]
  (if-let [old-entities (mapcat find-by-external-id external_ids)]
    (let [old-entity (some-> old-entities
                             first
                             :entity
                             (with-long-id services)
                             ent/un-store)]
      (when (< 1 (count old-entities))
        (log/warn
         (format
          (str "More than one entity is "
               "linked to the external ids %s (examples: %s)")
          external_ids
          (->> (take 10 old-entities) ;; prevent very large logs
               (map (comp :id :entity))
               pr-str))))
      (cond-> entity-data
        ;; only one entity linked to the external ID
        old-entity (assoc :result "exists"
                          :id (:id old-entity))))
    entity-data))

(s/defschema WithExistingEntitiesServices
  (csu/open-service-schema
    {;; for `find-by-external-ids`
     :StoreService {:get-store GetStoreFn}
     ;; for `with-existing-entity`
     :CTIAHTTPServerService {:get-port (s/=> (s/constrained s/Int pos?))}
     ;; for `with-existing-entity`
     :ConfigService (-> external-svc-fns/ConfigServiceFns
                        (csu/select-all-keys #{:get-in-config}))}))

(s/defn with-existing-entities :- [EntityImportData]
  "Add existing entities to the import data map."
  [import-data entity-type identity-map
   services :- WithExistingEntitiesServices]
  (let [entities-by-external-id
        (by-external-id
         (find-by-external-ids import-data
                               entity-type
                               identity-map
                               services))
        find-by-external-id-fn (fn [external_id]
                                 (when external_id
                                   (get entities-by-external-id
                                        {:external_id external_id})))]
    (map #(with-existing-entity % find-by-external-id-fn services)
         import-data)))

(s/defn prepare-import :- BundleImportData
  "Prepares the import data by searching all existing
   entities based on their external IDs. Only new entities
   will be imported"
  [bundle-entities
   external-key-prefixes
   auth-identity
   services :- APIHandlerServices]
  (map-kv (fn [k v]
            (let [entity-type (bulk/entity-type-from-bulk-key k)]
              (-> v
                  (init-import-data entity-type external-key-prefixes)
                  (with-existing-entities entity-type auth-identity services))))
          bundle-entities))

(defn create?
  "Whether the provided entity should be created or not"
  [{:keys [result]}]
  ;; Add only new entities without error
  (not (contains? #{"error" "exists"} result)))

(s/defn prepare-bulk
  "Creates the bulk data structure with all entities to create."
  [bundle-import-data :- BundleImportData]
  (map-kv
   (fn [_ v]
     (->> v
          (filter create?)
          (remove nil?)
          (map :new-entity)))
   bundle-import-data))

(s/defn with-bulk-result
  "Set the bulk result to the bundle import data"
  [bundle-import-data :- BundleImportData
   bulk-result]
  (map-kv (fn [k v]
            (let [{submitted true
                   not-submitted false} (group-by create? v)]
              (concat
               ;; Only submitted entities are processed
               (map (fn [entity-import-data
                         {:keys [error msg] :as entity-bulk-result}]
                      (cond-> entity-import-data
                        error (assoc :error error
                                     :result "error")
                        msg (assoc :msg msg)
                        (not error) (assoc :id entity-bulk-result
                                           :result "created")))
                    submitted (get bulk-result k))
               not-submitted)))
          bundle-import-data))

(s/defn build-response :- BundleImportResult
  "Build bundle import response"
  [bundle-import-data :- BundleImportData]
  {:results (map
             #(dissoc % :new-entity :old-entity)
             (apply concat (vals bundle-import-data)))})

(defn bulk-params [get-in-config]
  {:refresh
   (get-in-config [:ctia :store :bundle-refresh] "false")})

(defn log-errors
  [response]
  (let [errors (->> response
                    :results
                    (filter :error))]
    (doseq [error errors]
      (log/warn error)))
  response)

(s/defn import-bundle :- BundleImportResult
  [bundle :- NewBundle
   external-key-prefixes :- (s/maybe s/Str)
   auth-identity :- (s/protocol auth/IIdentity)
   {{:keys [get-in-config]} :ConfigService
    :as services} :- APIHandlerServices]
  (let [bundle-entities (select-keys bundle bundle-entity-keys)
        bundle-import-data (prepare-import bundle-entities
                                           external-key-prefixes
                                           auth-identity
                                           services)
        bulk (debug "Bulk" (prepare-bulk bundle-import-data))
        tempids (->> bundle-import-data
                     (map (fn [[_ entities-import-data]]
                            (entities-import-data->tempids entities-import-data)))
                     (apply merge {}))]
    (debug "Import bundle response"
           (->> (bulk/create-bulk bulk tempids auth-identity (bulk-params get-in-config) services)
                (with-bulk-result bundle-import-data)
                build-response
                log-errors))))

(defn bundle-max-size [get-in-config]
  (bulk/get-bulk-max-size get-in-config))

(defn bundle-size
  [bundle]
  (bulk/bulk-size
   (select-keys bundle
                bundle-entity-keys)))

(s/defn local-entity?
  "Returns true if this entity'ID is hosted by this CTIA instance,
   false otherwise"
  [id services :- HTTPShowServices]
  (if (seq id)
    (if (id/long-id? id)
      (let [id-rec (id/long-id->id id)
            this-host (:hostname (p/get-http-show services))]
        (= (:hostname id-rec) this-host))
      true)
    false))

(defn clean-bundle
  [bundle]
  (->> (fmap #(remove nil? %) bundle)
       (filter (comp seq second))
       (into {})))

(def ^:dynamic correlation-id nil)

(defn- get-epoch-second []
  (.getEpochSecond (Instant/now)))

(s/defn fetch-nodes
  "given relationships, fetch all related objects"
  [relationships identity-map
   {{:keys [send-event]} :RiemannService
    :as services} :- APIHandlerServices]
  (when (seq relationships)
    (let [all-ids (into #{}
                        (comp (mapcat (juxt :target_ref :source_ref))
                              (distinct)
                              (filter #(local-entity? % services)))
                        relationships)
          by-type (dissoc (group-by #(ent/id->entity-type % services) all-ids) nil)
          by-bulk-key (into {}
                            (map (fn [[k v]]
                                   [(bulk/bulk-key (keyword k)) v]))
                            by-type)
          start (System/currentTimeMillis)
          fetched (bulk/fetch-bulk by-bulk-key identity-map services)]
      (send-event {:service "Export bundle fetch relationships targets"
                   :correlation-id correlation-id
                   :time (get-epoch-second)
                   :event-type "export-bundle"
                   :metric (- (System/currentTimeMillis) start)})
      (clean-bundle fetched))))

(defn- into-acc [acc f data]
  (reduce-kv
   (fn [acc id records]
     (update acc id concat records))
   acc
   (group-by f data)))

(defn- scroll-with-limit
  "Extension for store/list-records method.

  First argument should be a function taking a single record and returning a value
  used to group paginated records.

  eg. `:source_ref` to fetch all records limited by `:source_ref` field.

  NOTE: `params-map` must contain `:sort` instruction with the fields based on which you wish to limit responses.
  NOTE: `params-map` must contain `:limit` instruction to set maximum amount of entities fetched by page.

  NOTE: `search_after` and `offset` usage
        The result of each invocation of `list-records` can have two main possible cases:

          1. all results belong to the same record
             in that case next call to `list-records` must use `search_after` to 'fast forward' the cursor to the next
             record id.
          2. results list begins with records belong to the same id but then the tail might have 1-N partitions.
             That means the last partition in result list is incomplete and we must use `offset` to fetch next page."
  ([f store filter-map identity-map params-map]
   (scroll-with-limit {} [] f store filter-map identity-map params-map))
  ([acc prev-data f store filter-map identity-map params-map]
   (let [{data :data {{:keys [offset search_after] :as next} :next} :paging}
         (store/list-records store filter-map identity-map params-map)
         params-map (select-keys params-map [:limit :sort])
         [chunk & chunks] (partition-by f data)]
     (if (nil? next)
       (into-acc acc f (concat prev-data data))
       (if (empty? chunks)
         (recur (into-acc acc f (concat prev-data chunk))
                [] f store filter-map identity-map
                ;; HACK The second element must contain a numeric value that is guaranteed not to be contained in any document.
                ;;      For the timestamp, the value is -1.
                ;;      Only in this case, using "search_after" will ensure that the cursor moves to the next identifier.
                (assoc params-map :search_after [(first search_after) -1]))
         (recur (into-acc acc f (apply concat prev-data chunk (butlast chunks)))
                (last chunks) f store filter-map identity-map
                (assoc params-map :offset offset)))))))

(defn relationships-filter [records related-to source-type target-type]
  {:query (cond->> [(format "%s:(%s)" (name related-to) (string/join " OR " (map #(format "\"%s\"" %) (map :id records))))]
            source-type (cons (format "source_ref:*%s*" (name source-type)))
            target-type (cons (format "target_ref:*%s*" (name target-type)))
            ;; NOTE reverse is important to ensure more strict filter `source_ref:("id1" OR "id2")`
            ;;      takes precedence over wildcard filter
            :always (reverse)
            :always (string/join " AND "))})

(defn fetch-relationships [records identity-map
                           {:keys [related_to
                                   source_type
                                   target_type]
                            :or {related_to [:source_ref :target_ref]}}
                           {{:keys [get-store]} :StoreService
                            {:keys [get-in-config]} :ConfigService
                            {:keys [send-event]} :RiemannService}]
  (when (seq records)
    (let [start (System/currentTimeMillis)
          store (get-store :relationship)
          limit (get-in-config [:ctia :http :bundle :export :max-relationships] 1000)
          res (into #{}
                    (comp
                     ;; sort by timestamp to mix relationships from :source_ref and :target_ref together
                     ;; use "reverse" comparator to sort from newest to oldest
                     (map #(sort-by :timestamp (fn [a b] (.after a b)) %))
                     ;; apply limit for each id to respect configured constraint
                     (mapcat #(take limit %)))
                    (vals
                     (transduce
                      ;; map over the list of desired related_to
                      ;; scroll-with-limit returns a mapping from record-id to relationships
                      (map #(scroll-with-limit % store
                                               (relationships-filter records % source_type target_type)
                                               identity-map
                                               {:limit limit
                                                :sort [{(name %) "desc"}
                                                       {"timestamp" "desc"}]}))
                      #(apply merge-with concat %&)
                      (set related_to))))]
      (send-event {:service "Export bundle fetch relationships"
                   :correlation-id correlation-id
                   :time (get-epoch-second)
                   :event-type "export-bundle"
                   :metric (- (System/currentTimeMillis) start)})
      res)))

(defn fetch-records [ids identity-map
                     {{:keys [get-store]} :StoreService
                      {:keys [send-event]} :RiemannService
                      :as services}]
  (let [start (System/currentTimeMillis)
        res (doall (sequence
                    (comp (mapcat (fn [[store ids]]
                                    (store/read-records store ids identity-map {})))
                          (map #(-> %
                                    ent/un-store
                                    (ent/with-long-id services))))
                    (group-by #(-> (ent/id->entity-type % services)
                                   (keyword)
                                   (get-store))
                              ids)))]
    (send-event {:service "Export bundle fetch record"
                 :correlation-id correlation-id
                 :time (get-epoch-second)
                 :event-type "export-bundle"
                 :metric (- (System/currentTimeMillis) start)})
    res))

(def empty-bundle
  {:type "bundle"
   :source "ctia"})

(defn- combine-bundle
  "Create a bundle from sets of records, relationships and its targets.
  Uses `empty-bundle` as basis."
  [records relationships targets]
  (let [records-bundle (group-by #(-> (:type %)
                                      (keyword)
                                      (bulk/bulk-key))
                                 records)]
    (deep-merge-with coll/add-colls
                     (reduce-kv
                      (fn [acc k v]
                        (assoc acc k (set v)))
                      empty-bundle
                      records-bundle)
                     (when (seq relationships)
                       {:relationships relationships})
                     targets)))

(s/defn export-bundle
  [ids identity-map ident params
   {{:keys [send-event]} :RiemannService
    :as services} :- APIHandlerServices]
  (binding [correlation-id (str (UUID/randomUUID))]
    (send-event {:service "Export bundle start"
                 :correlation-id correlation-id
                 :time (get-epoch-second)
                 :event-type "export-bundle"
                 :metric (count ids)})
    (let [start (System/currentTimeMillis)
          records (fetch-records (distinct ids) identity-map services)
          relationships (when (:include_related_entities params true)
                          (map #(ent/with-long-id % services)
                               (fetch-relationships records identity-map params services)))
          targets (fetch-nodes relationships ident services)
          res (combine-bundle records relationships targets)]
      (send-event {:service "Export bundle end"
                   :correlation-id correlation-id
                   :time (get-epoch-second)
                   :event-type "export-bundle"
                   :metric (- (System/currentTimeMillis) start)})
      res)))
