(ns ctia.bundle.core
  (:refer-clojure :exclude [identity])
  (:require
   [clj-momo.lib.map :refer [deep-merge-with]]
   [clojure
    [set :as set]
    [string :as string]]
   [clojure.tools.logging :as log]
   [ctia
    [auth :as auth]
    [properties :as p]
    [store :refer [list-fn
                   read-fn
                   read-store]]]
   [ctia.lib.collection :as coll :refer [fmap]]
   [ctia.bulk.core :as bulk]
   [ctia.bundle.schemas
    :refer
    [BundleImportData BundleImportResult EntityImportData]]
   [ctia.domain.entities :as ent :refer [with-long-id]]
   [ctia.schemas.core :refer [APIHandlerServices NewBundle TempIDs]]
   [ctim.domain.id :as id]
   [schema.core :as s]))

(def find-by-external-ids-limit 1000)

(def bundle-entity-keys
  (set (vals bulk/bulk-entity-mapping)))

(defn transient-id?
  [id]
  (and id (re-matches id/transient-id-re id)))

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
      (transient-id? id) (assoc :original_id id)
      (seq filtered-ext-ids) (assoc :external_ids filtered-ext-ids))))

(defn all-pages
  "Retrieves all external ids using pagination."
  [f]
  (loop [paging {:offset 0
                 :limit find-by-external-ids-limit}
         entities []]
    (let [{results :data
           {next-page :next} :paging} (f paging)
          acc-entities (into entities results)]
      (if next-page
        (recur next-page acc-entities)
        acc-entities))))

(defn find-by-external-ids
  [import-data entity-type auth-identity]
  (let [external-ids (mapcat :external_ids import-data)]
    (log/debugf "Searching %s matching these external_ids %s"
                entity-type
                (pr-str external-ids))
    (if (seq external-ids)
      (debug (format "Results for %s:" (pr-str external-ids))
             (all-pages
              (fn [paging]
                (read-store entity-type list-fn
                            {:all-of {:external_ids external-ids}}
                            (auth/ident->map auth-identity)
                            paging))))
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
   find-by-external-ids]
  (if-let [old-entities (mapcat find-by-external-ids external_ids)]
    (let [old-entity (some-> old-entities
                             first
                             :entity
                             with-long-id
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

(s/defn with-existing-entities :- [EntityImportData]
  "Add existing entities to the import data map."
  [import-data entity-type identity-map]
  (let [entities-by-external-id
        (by-external-id
         (find-by-external-ids import-data
                               entity-type
                               identity-map))
        find-by-external-id-fn (fn [external_id]
                                 (when external_id
                                   (get entities-by-external-id
                                        {:external_id external_id})))]
    (map #(with-existing-entity % find-by-external-id-fn)
         import-data)))

(s/defn prepare-import :- BundleImportData
  "Prepares the import data by searching all existing
   entities based on their external IDs. Only new entities
   will be imported"
  [bundle-entities
   external-key-prefixes
   auth-identity]
  (map-kv (fn [k v]
            (let [entity-type (bulk/entity-type-from-bulk-key k)]
              (-> v
                  (init-import-data entity-type external-key-prefixes)
                  (with-existing-entities entity-type auth-identity))))
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

(defn bulk-params []
  {:refresh
   (p/get-in-global-properties [:ctia :store :bundle-refresh] "false")})

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
   services :- APIHandlerServices]
  (let [bundle-entities (select-keys bundle bundle-entity-keys)
        bundle-import-data (prepare-import bundle-entities
                                           external-key-prefixes
                                           auth-identity)
        bulk (debug "Bulk" (prepare-bulk bundle-import-data))
        tempids (->> bundle-import-data
                     (map (fn [[_ entities-import-data]]
                            (entities-import-data->tempids entities-import-data)))
                     (apply merge {}))]
    (debug "Import bundle response"
           (->> (bulk/create-bulk bulk tempids auth-identity (bulk-params) services)
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

(defn local-entity?
  "Returns true if this entity'ID is hosted by this CTIA instance,
   false otherwise"
  [id]
  (if (seq id)
    (if (id/long-id? id)
      (let [id-rec (id/long-id->id id)
            this-host (p/get-in-global-properties [:ctia :http :show :hostname])]
        (= (:hostname id-rec) this-host))
      true)
    false))

(defn clean-bundle
  [bundle]
  (->> (fmap #(remove nil? %) bundle)
       (filter (comp seq second))
       (into {})))

(s/defn fetch-relationship-targets
  "given relationships, fetch all related objects"
  [relationships identity-map services :- APIHandlerServices]
  (let [all-ids (->> relationships
                     (map (fn [{:keys [target_ref source_ref]}]
                            [target_ref source_ref]))
                     flatten
                     set
                     (filter local-entity?)
                     set)
        by-type (dissoc (group-by
                         #(ent/long-id->entity-type %) all-ids) nil)
        by-bulk-key (into {}
                          (map (fn [[k v]]
                                 {(bulk/bulk-key
                                   (keyword k)) v}) by-type))
        fetched (bulk/fetch-bulk by-bulk-key identity-map services)]
    (clean-bundle fetched)))

(defn relationships-filters
  [id
   {:keys [related_to
           source_type
           target_type]
    :or {related_to #{:source_ref :target_ref}}}]
  (let [edge-filters (->> (map #(hash-map % id) (set related_to))
                          (apply merge))
        node-filters (cond->> []
                       source_type (cons (format "source_ref:*%s*" (name source_type)))
                       target_type (cons (format "target_ref:*%s*" (name target_type)))
                       :always (string/join " AND "))]
    (into {:one-of edge-filters}
          (when (seq node-filters)
            {:query node-filters}))))

(defn fetch-entity-relationships
  "given an entity id, fetch all related relationship"
  [id
   identity-map
   filters]
  (let [filter-map (relationships-filters id filters)
        max-relationships (p/get-in-global-properties [:ctia :http :bundle :export :max-relationships]
                                                      1000)]
    (some-> (:data (read-store :relationship
                               list-fn
                               filter-map
                               identity-map
                               {:limit max-relationships
                                :sort_by "timestamp"
                                :sort_order "desc"}))
            ent/un-store-all)))

(defn fetch-record
  "Fetch a record by ID guessing its type"
  [id identity-map]
  (when-let [entity-type (ent/id->entity-type id)]
    (read-store (keyword entity-type)
                read-fn
                id
                identity-map
                {})))

(s/defn export-entities
  "Given an entity id, export it along
   with its relationship as a Bundle"
  [id
   identity-map
   ident
   params
   services :- APIHandlerServices]
  (if-let [record (fetch-record id identity-map)]
    (let [relationships (when (:include_related_entities params true)
                          (fetch-entity-relationships id identity-map params))]
      (cond-> {}
        record
        (assoc (-> (:type record)
                   keyword
                   bulk/bulk-key)
               #{(-> record
                     ent/un-store
                     ent/with-long-id)})

        (seq relationships)
        (assoc :relationships
               (set (map ent/with-long-id relationships)))

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
  [ids
   identity-map
   ident
   params
   services :- APIHandlerServices]
  (->> (map #(export-entities % identity-map ident params services) ids)
       (reduce #(deep-merge-with coll/add-colls %1 %2))
       (into empty-bundle)))
