(ns ctia.bundle.core
  (:refer-clojure :exclude [identity])
  (:require [clojure
             [set :as set]
             [string :as string]]
            [clojure.tools.logging :as log]
            [ctia
             [auth :as auth]
             [properties :refer [properties]]
             [store :refer [read-fn list-fn read-store]]]
            [ctia.bulk.core :as bulk]
            [ctia.bundle.schemas
             :refer
             [BundleImportData BundleImportResult EntityImportData]]
            [ctia.domain.entities :as ent :refer [with-long-id]]
            [ctia.schemas.core
             :refer [NewBundle TempIDs]]
            [ctim.domain.id :as id]
            [schema.core :as s]))

(def bundle-mapping
  {:actor :actors
   :attack_pattern :attack_patterns
   :campaign :campaigns
   :coa :coas
   :data_table :data_tables
   :exploit_target :exploit_targets
   :feedback :feedbacks
   :incident :incidents
   :investigation :investigations
   :indicator :indicators
   :judgement :judgements
   :malware :malwares
   :relationship :relationships
   :casebook :casebooks
   :sighting :sightings
   :tool :tools})

(def bundle-entity-keys
  (set (vals bundle-mapping)))

(defn entity-type-from-bundle-key
  "Converts a bundle entity key to an entity type
   Ex: :attack_patterns -> :attack-pattern"
  [bundle-key]
  (bulk/entity-type-from-bulk-key bundle-key))

(defn bulk-key
  "Converts a bundle key to a bulk key"
  [bundle-key]
  bundle-key)

(defn transient-id?
  [id]
  (and id (re-matches id/transient-id-re id)))

(def default-external-key-prefixes "ctia-")

(defn debug [msg v]
  (log/debug msg v)
  v)

(s/defn prefixed-external-ids :- [s/Str]
  "Returns all external IDs prefixed by the given key-prefix."
  [key-prefix external-ids]
  (filter #(string/starts-with? % key-prefix) external-ids))

(s/defn valid-external-id :- (s/maybe s/Str)
  "Returns the external ID that can be used to check whether an entity has
  already been imported or not."
  [external-ids key-prefixes]
  (let [valid-external-ids (mapcat #(prefixed-external-ids % external-ids)
                                   key-prefixes)]
    (if (> (count valid-external-ids) 1)
      (log/warnf (str "More than 1 valid external ID has been found "
                      "(key-prefixes:%s | external-ids:%s)")
                 (pr-str key-prefixes) (pr-str external-ids))
      (first valid-external-ids))))

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
  (let [key-prefixes (parse-key-prefixes
                      (or external-key-prefixes
                          (get-in @properties
                                  [:ctia :store :external-key-prefixes]
                                  default-external-key-prefixes)))
        external_id (valid-external-id external_ids key-prefixes)]
    (when-not external_id
      (log/warnf "No valid external ID has been provided (id:%s)" id))
    (cond-> {:new-entity entity
             :type entity-type}
      (transient-id? id) (assoc :original_id id)
      external_id (assoc :external_id external_id))))

(def find-by-external-ids-limit 1000)

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
  (let [external-ids (keep :external_id import-data)]
    (log/debugf "Searching %s matching these external_ids %s"
                entity-type
                (pr-str external-ids))
    (if (seq external-ids)
      (debug (format "Results for %s:" (pr-str external-ids))
             (all-pages
              (fn [paging]
                (read-store entity-type list-fn
                            {:external_ids external-ids}
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
  [{:keys [external_id]
    entity-type :type
    :as entity-data} :- EntityImportData
   entity-type
   find-by-external-id]
  (if-let [old-entities (find-by-external-id external_id)]
    (let [old-entity (some-> old-entities
                             first
                             :entity
                             with-long-id
                             ent/un-store)
          num-old-entities (count old-entities)]
      (cond-> entity-data
        ;; only one entity linked to the external ID
        (and old-entity
             (= num-old-entities 1)) (assoc :result "exists"
                                            ;;:old-entity old-entity
                                            :id (:id old-entity))
        ;; more than one entity linked to the external ID
        (> num-old-entities 1)
        (assoc :result "error"
               :error
               (format
                (str "More than one entity is "
                     "linked to the external id %s (%s)")
                external_id
                (pr-str (map :id old-entities))))))
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
    (map #(with-existing-entity % entity-type find-by-external-id-fn)
         import-data)))

(s/defn prepare-import :- BundleImportData
  "Prepares the import data by searching all existing
   entities based on their external IDs. Only new entities
   will be imported"
  [bundle-entities
   external-key-prefixes
   auth-identity]
  (map-kv (fn [k v]
            (let [entity-type (entity-type-from-bundle-key k)]
              (-> v
                  (init-import-data entity-type external-key-prefixes)
                  (with-existing-entities entity-type auth-identity))))
          bundle-entities))

(defn create?
  "Whether the provided entity should be created or not"
  [{:keys [result] :as entity}]
  ;; Add only new entities without error
  (not (contains? #{"error" "exists"} result)))

(s/defn with-bulk-keys
  "Renames all map keys from bundle to bulk format
   (ex: attack_patterns -> attack-patterns)"
  [m]
  (->> m
       (map (fn [[k v]]
              [(bulk-key k) v]))
       (into {})))

(s/defn prepare-bulk
  "Creates the bulk data structure with all entities to create."
  [bundle-import-data :- BundleImportData]
  (->> bundle-import-data
       (map-kv (fn [k v]
                 (->> v
                      (filter create?)
                      (remove nil?)
                      (map :new-entity))))
       with-bulk-keys))

(s/defn with-bulk-result
  "Set the bulk result to the bundle import data"
  [bundle-import-data :- BundleImportData
   {:keys [tempids] :as bulk-result}]
  (map-kv (fn [k v]
            (let [{submitted true
                   not-submitted false} (group-by create? v)]
              (concat
               ;; Only submitted entities are processed
               (map (fn [entity-import-data
                         {:keys [error] :as entity-bulk-result}]
                      (cond-> entity-import-data
                        error (assoc :error error
                                     :result "error")
                        (not error) (assoc :id entity-bulk-result
                                           :result "created")))
                    submitted (get bulk-result (bulk-key k)))
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
   (get-in @properties [:ctia :store :bundle-refresh] "false")})

(s/defn import-bundle :- BundleImportResult
  [bundle :- NewBundle
   external-key-prefixes :- (s/maybe s/Str)
   auth-identity :- (s/protocol auth/IIdentity)]
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
           (->> (bulk/create-bulk bulk tempids auth-identity (bulk-params))
                (with-bulk-result bundle-import-data)
                build-response))))

(def bundle-max-size bulk/get-bulk-max-size)

(defn bundle-size
  [bundle]
  (bulk/bulk-size (select-keys bundle bundle-entity-keys)))

(defn bulk-keys->bundle-keys [bulk]
  (apply merge
         (map (fn [[k v]]
                (when-let [bundle-key (get bundle-mapping (keyword k))]
                  {bundle-key v})) bulk)))

(defn fetch-relationship-targets
  "given relationships, fetch all related objects"
  [relationships identity-map]
  (let [all-ids
        (apply set/union
               (map (fn [{:keys [target_ref
                                 source_ref]}]
                      #{target_ref source_ref})
                    relationships))
        by-type (group-by
                 #(ent/long-id->entity-type %)
                 all-ids)]
    (bulk-keys->bundle-keys
     (bulk/fetch-bulk by-type identity-map))))

(defn fetch-entity-rels
  "given an entity id, fetch all related relationship"
  [id identity-map]
  (let [entity-type (ent/id->entity-type id)
        params [{:source_ref id}
                {:target_ref id}]
        rel-lists
        (map #(some->
               (read-store :relationship
                           list-fn
                           %
                           identity-map
                           {})
               :data
               ent/un-store-all
               set)
             params)]
    (apply set/union rel-lists)))

(defn fetch-record
  "Fetch a record by ID guessing its type"
  [id identity-map]
  (when-let [entity-type (ent/id->entity-type id)]
    (read-store (keyword entity-type)
                read-fn
                id
                identity-map
                {})))

(def empty-bundle
  {:id (id/make-transient-id "bundle")
   :type "bundle"
   :source "ctia"
   :valid_time {}})

(defn export-bundle
  "Given an entity id, export it along
   with its relationship as a Bundle"
  [id
   identity-map
   ident
   {:keys [include_related_entities]
    :or {include_related_entities true}}]
  (if-let [record (fetch-record id identity-map)]
    (let [relationships (fetch-entity-rels id identity-map)]
      (cond-> empty-bundle
        (seq relationships) (assoc :relationships relationships)
        (and (seq relationships)
             include_related_entities) (into (fetch-relationship-targets
                                              relationships ident))))
    empty-bundle))
