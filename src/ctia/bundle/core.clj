(ns ctia.bundle.core
  (:require
   [clj-momo.lib.map :refer [deep-merge-with]]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [ctia.auth :as auth]
   [ctia.bulk.core :as bulk]
   [ctia.bundle.schemas :refer
    [BundleImportData BundleImportResult EntityImportData
     FindByExternalIdsServices]]
   [ctia.entity.entities :as entities]
   [ctia.domain.entities :as ent :refer [with-long-id]]
   [ctia.lib.collection :as coll]
   [ctia.properties :as p]
   [ctia.schemas.core :as schemas :refer
    [APIHandlerServices HTTPShowServices NewBundle TempIDs]]
   [ctia.schemas.services :as external-svc-fns]
   [ctia.schemas.utils :as csu]
   [ctia.store :as store]
   [ctia.store-service.schemas :refer [GetStoreFn]]
   [ctim.domain.id :as id]
   [ring.util.http-response :refer [bad-request!]]
   [schema.core :as s]
   [schema-tools.core :as st])
  (:import
   [java.time Instant]
   [java.util UUID]
   [java.util.concurrent ExecutionException]))

(s/defschema BundleImportMode (s/enum :create :patch))

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
    (if (empty? ext-ids)
      entities
      (let [query {:all-of {:external_ids ext-ids}}
            paging {:limit find-by-external-ids-limit}
            {results :data
             {next-page :next} :paging} (-> (get-store entity-type)
                                            (store/list-records
                                              query
                                              (auth/ident->map auth-identity)
                                              paging))
            acc-entities (into entities results)
            matched-ext-ids (into #{} (mapcat :external_ids) results)
            remaining-ext-ids (into [] (remove matched-ext-ids) ext-ids)]
        (if next-page
          (recur remaining-ext-ids acc-entities)
          acc-entities)))))

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

(s/defn find-by-asset_refs :- {s/Str (s/pred map?)}
  [asset_refs :- #{s/Str}
   entity-type
   auth-identity
   {{:keys [get-store]} :StoreService} :- FindByExternalIdsServices]
  (if (empty? asset_refs)
    {}
    (let [_ (log/debugf "Searching %s matching these asset_refs %s"
                        entity-type
                        (pr-str asset_refs))
          entities (loop [asset_refs asset_refs
                          entities {}]
                     (if (empty? asset_refs)
                       entities
                       (let [query {:all-of {:asset_ref asset_refs}}
                             paging {:limit find-by-external-ids-limit}
                             {results :data
                              {next-page :next} :paging} (-> (get-store entity-type)
                                                             (store/list-records
                                                               query
                                                               (auth/ident->map auth-identity)
                                                               paging))
                             entities (into entities (map (juxt :asset_ref identity))
                                            results)]
                         (if next-page
                           (recur (apply disj asset_refs (map :asset_ref results))
                                  entities)
                           entities))))]
      (debug (format "Results searching %s for asset_refs %s:" entity-type (pr-str asset_refs))
             entities))))

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
  (assert ((some-fn nil? schemas/transient-id?) (:id entity-data))
          "TODO support realized id")
  (let [old-entities (mapcat find-by-external-id external_ids)
        old-entity (some-> (first old-entities)
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
      old-entity (-> (assoc :result "exists"
                            :id (:id old-entity))
                     (assoc-in [:new-entity :id] (:id old-entity))))))

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
   entities based on their external IDs. New entities
   will be created, and existing entities will be patched."
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

(s/defn create? :- s/Bool
  "Whether the provided entity should be created or not"
  [{:keys [result]}]
  ;; Add only new entities without error
  (not (contains? #{"error" "exists"} result)))

(s/defn patch? :- s/Bool
  "Whether the provided entity should be patched or not"
  [{:keys [result]}]
  ;; Add only new entities without error
  (not= "error" result))

(s/defn prepare-bulk
  "Creates the bulk data structure with all entities to create or patch."
  [bundle-import-data :- BundleImportData
   tempids :- TempIDs]
  (reduce-kv (fn [acc k vs]
               (reduce (fn [acc v]
                         (let [op (when v
                                    (cond
                                      (create? v) :creates-bulk
                                      (patch? v) :patches-bulk))]
                           (cond-> acc
                             op (update-in [op k] (fnil conj []) (:new-entity v)))))
                       acc vs))
             {:creates-bulk {}
              :patches-bulk {}}
             bundle-import-data))

(s/defn with-bulk-result :- BundleImportData
  "Set the bulk result to the bundle import data"
  [bundle-import-data :- BundleImportData
   mode :- BundleImportMode
   bulk-result :- bulk/BulkRefs]
  (map-kv (fn [k v]
            (let [submitted (filter (case mode
                                      :create create?
                                      :patch patch?)
                                    v)]
              (mapv (s/fn :- EntityImportData
                      [entity-import-data
                       {:keys [error msg] :as entity-bulk-result}]
                      (let [error (or error
                                      ;; FIXME patch-entities can return {:indicators {:errors {:not-found (nil)}}},
                                      ;; which gets translated to :error [:errors {:not-found (nil)} somehow.
                                      ;; delete this code. I think I need to add :enveloped-result? support to patch-entities.
                                      (when-not (string? entity-bulk-result)
                                        entity-bulk-result))]
                        (cond-> entity-import-data
                          error (assoc :error error
                                       :result "error")
                          msg (assoc :msg msg)
                          (not error) (assoc :id entity-bulk-result
                                             :result (case mode
                                                       :create "created"
                                                       :patch "updated")))))
                    submitted (get bulk-result k))))
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

(defn entity->bundle-keys
  "For given entity key returns corresponding keys that may be present in Bundle schema.
  e.g. :asset => [:assets :asset_refs]"
  [entity-key]
  (let [{:keys [entity plural]} (get (entities/all-entities) entity-key)
        kw->snake-case-str      (fn [kw] (-> kw name (string/replace #"-" "_")))]
    [(-> plural kw->snake-case-str keyword)
     (-> entity kw->snake-case-str (str "_refs") keyword)]))

(s/defn prep-bundle-schema :- s/Any
  "Remove keys of disabled entities from Bundle schema"
  [{{:keys [entity-enabled?]} :FeaturesService} :- APIHandlerServices]
  (->> (entities/all-entities)
       keys
       (remove entity-enabled?)
       (mapcat entity->bundle-keys)
       (apply st/dissoc NewBundle)))

(s/defn resolve-asset-properties+mappings :- BundleImportData
  [bundle-import-data :- BundleImportData
   tempids :- TempIDs
   auth-identity
   services :- FindByExternalIdsServices]
  (let [resolve* (s/fn :- BundleImportData
                   [bundle-import-data :- BundleImportData
                    bulk-asset-kw :- (s/enum :asset_properties :asset_mappings)]
                   (let [asset_refs (into #{} (keep (fn [{:keys [id] {:keys [asset_ref]} :new-entity :as import-data}]
                                                      (when (and (nil? id) asset_ref)
                                                        (let [asset_ref (get tempids asset_ref asset_ref)]
                                                          (when-not (schemas/transient-id? asset_ref)
                                                            asset_ref)))))
                                          (bulk-asset-kw bundle-import-data))
                         asset_ref->old-entity (find-by-asset_refs asset_refs
                                                                   (bulk/entity-type-from-bulk-key bulk-asset-kw)
                                                                   auth-identity
                                                                   services)]
                     (cond-> bundle-import-data
                       (seq (bulk-asset-kw bundle-import-data))
                       (update bulk-asset-kw
                               (fn [bulk-assets]
                                 (into []
                                       (map (fn [{:keys [id] {:keys [asset_ref]} :new-entity :as import-data}]
                                              (or (when-not id
                                                    (when-some [asset_ref (get tempids asset_ref asset_ref)]
                                                      (when-some [{:keys [id] :as old-entity} (asset_ref->old-entity (get tempids asset_ref asset_ref))]
                                                        (-> import-data
                                                            (assoc :old-entity old-entity)
                                                            (update :new-entity assoc :id id :asset_ref asset_ref)
                                                            (assoc :id id)))))
                                                  import-data)))
                                       bulk-assets))))))]
    (-> bundle-import-data
        (resolve* :asset_mappings)
        (resolve* :asset_properties))))

(defn bundle-import-data->tempids
  [bundle-import-data
   tempids]
  (into {} (map entities-import-data->tempids)
        (vals bundle-import-data)))

(s/defn import-bundle :- BundleImportResult
  [bundle :- (st/optional-keys-schema NewBundle)
   external-key-prefixes :- (s/maybe s/Str)
   auth-identity :- (s/protocol auth/IIdentity)
   {{:keys [get-in-config]} :ConfigService
    :as services} :- APIHandlerServices]
  (let [bundle-entities (select-keys bundle bundle-entity-keys)
        bundle-import-data (prepare-import bundle-entities
                                           external-key-prefixes
                                           auth-identity
                                           services)
        tempids (bundle-import-data->tempids bundle-import-data {})
        bundle-import-data (resolve-asset-properties+mappings bundle-import-data tempids auth-identity services)
        tempids (bundle-import-data->tempids bundle-import-data tempids)
        {:keys [creates-bulk patches-bulk] :as _all-bulks} (debug "Bulk" (prepare-bulk bundle-import-data tempids))
        ;; throw 400 response on partial creates before mutating db
        _ (when (seq creates-bulk)
            (let [create-bundle-schema (-> (prep-bundle-schema services)
                                           (st/dissoc :source))
                  creates-bulk (update-vals creates-bulk set)]
              (when-some [fail (s/check create-bundle-schema creates-bulk)]
                (bad-request! {:errors fail}))))
        {:keys [tempids] :as create-bulk-refs} (bulk/create-bulk creates-bulk tempids auth-identity (bulk-params get-in-config) services)
        create-result (with-bulk-result bundle-import-data :create (dissoc create-bulk-refs :tempids))
        patch-result (let [patch-bulk-refs (bulk/patch-bulk patches-bulk tempids auth-identity (bulk-params get-in-config) services
                                                            {:enveloped-result? true})]
                       (with-bulk-result
                         bundle-import-data
                         :patch
                         (dissoc patch-bulk-refs :tempids)))]
    (debug "Import bundle response"
           (-> (merge-with into create-result patch-result)
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
  (reduce-kv
   (fn [acc k v]
     (if-let [v' (seq (remove nil? v))]
       (assoc acc k v')
       acc))
   {}
   bundle))

(def ^:dynamic correlation-id nil)

(defn- get-epoch-second []
  (.getEpochSecond (Instant/now)))

(s/defn fetch-relationship-targets
  "given relationships, fetch all related objects"
  [relationships identity-map
   {{:keys [send-event]} :RiemannService
    :as services} :- APIHandlerServices]
  (let [all-ids (->> relationships
                     (map (fn [{:keys [target_ref source_ref]}]
                            [target_ref source_ref]))
                     flatten
                     set
                     (filter #(local-entity? % services))
                     set)
        by-type (dissoc (group-by
                         #(ent/long-id->entity-type %) all-ids) nil)
        by-bulk-key (into {}
                          (map (fn [[k v]]
                                 {(bulk/bulk-key
                                   (keyword k)) v}) by-type))
        start (System/currentTimeMillis)
        fetched (bulk/fetch-bulk by-bulk-key identity-map services)]
    (send-event {:service "Export bundle fetch relationships targets"
                 :correlation-id correlation-id
                 :time (get-epoch-second)
                 :event-type "export-bundle"
                 :metric (- (System/currentTimeMillis) start)})
    (clean-bundle fetched)))

(defn node-filters [field entity-types]
  (->> entity-types
       (map name)
       (map #(format "%s:*%s*" field %))
       (clojure.string/join " OR ")
       (format "(%s)")))

(defn relationships-filters
  [id
   {:keys [related_to
           source_type
           target_type]
    :or {related_to #{:source_ref :target_ref}}}]
  (let [edge-filters (->> (map #(hash-map % id) (set related_to))
                          (apply merge))
        node-filters (cond->> []
                       (seq source_type) (cons (node-filters "source_ref" source_type))
                       (seq target_type) (cons (node-filters "target_ref" target_type))
                       :always (string/join " AND "))]
    (into {:one-of edge-filters}
          (when (seq node-filters)
            {:query node-filters}))))

(s/defn fetch-entity-relationships
  "given an entity id, fetch all related relationship"
  [id
   identity-map
   filters
   {{:keys [get-in-config]} :ConfigService
    {:keys [get-store]} :StoreService
    {:keys [send-event]} :RiemannService} :- APIHandlerServices]
  (let [filter-map (relationships-filters id filters)
        max-relationships (get-in-config [:ctia :http :bundle :export :max-relationships]
                                         1000)
        start (System/currentTimeMillis)
        res (some-> (get-store :relationship)
                    (store/list-records
                     filter-map
                     identity-map
                     {:limit max-relationships
                      :sort_by "timestamp"
                      :sort_order "desc"})
                    :data
                    ent/un-store-all)]
    (send-event {:service "Export bundle fetch relationships"
                 :correlation-id correlation-id
                 :time (get-epoch-second)
                 :event-type "export-bundle"
                 :metric (- (System/currentTimeMillis) start)})
    res))

(s/defn fetch-record
  "Fetch a record by ID guessing its type"
  [id identity-map
   {{:keys [get-store]} :StoreService
    {:keys [send-event]} :RiemannService
    :as services} :- APIHandlerServices]
  (when-let [entity-type (ent/id->entity-type id services)]
    (let [start (System/currentTimeMillis)
          res (-> (get-store (keyword entity-type))
                  (store/read-record
                   id
                   identity-map
                   {:suppress-access-control-error? true}))]
      (send-event {:service "Export bundle fetch record"
                   :correlation-id correlation-id
                   :time (get-epoch-second)
                   :event-type "export-bundle"
                   :metric (- (System/currentTimeMillis) start)})
      res)))

(s/defn export-entities
  "Given an entity id, export it along
   with its relationship as a Bundle"
  [id
   identity-map
   ident
   params
   services :- APIHandlerServices]
  (if-let [record (fetch-record id identity-map services)]
    (let [relationships (when (:include_related_entities params true)
                          (fetch-entity-relationships id identity-map params services))]
      (cond-> {}
        record
        (assoc (-> (:type record)
                   keyword
                   bulk/bulk-key)
               #{(-> record
                     ent/un-store
                     (ent/with-long-id services))})

        (seq relationships)
        (assoc :relationships
               (set (map #(ent/with-long-id % services) relationships)))

        (seq relationships)
        (->> (deep-merge-with coll/add-colls
                              (fetch-relationship-targets
                               relationships
                               ident
                               services)))))
    {}))

(def empty-bundle
  {:type "bundle"
   :source "ctia"})

(s/defn export-bundle
  [ids identity params
   {{:keys [send-event]} :RiemannService
    :as services} :- APIHandlerServices]
  (if (seq ids)
    (binding [correlation-id (str (UUID/randomUUID))]
      (send-event {:service "Export bundle start"
                   :correlation-id correlation-id
                   :time (get-epoch-second)
                   :event-type "export-bundle"
                   :metric (count ids)})
      (let [start (System/currentTimeMillis)
            identity-map (auth/ident->map identity)
            entities (try
                       (->> ids
                            (pmap #(export-entities % identity-map identity params services))
                            (into []))
                       (catch ExecutionException e
                         (throw (.getCause e))))
            res (->> entities
                     (reduce #(deep-merge-with coll/add-colls %1 %2))
                     (into empty-bundle))]
        (send-event {:service "Export bundle end"
                     :correlation-id correlation-id
                     :time (get-epoch-second)
                     :event-type "export-bundle"
                     :metric (- (System/currentTimeMillis) start)})
        res))
    empty-bundle))
