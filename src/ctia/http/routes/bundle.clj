(ns ctia.http.routes.bundle
  (:require [compojure.api.sweet :refer :all]
            [ctia.http.routes.bulk :as bulk]
            [ctia.lib.keyword :refer [singular]]
            [ctia.properties :refer [properties]]
            [ctia.schemas.bulk :refer [Bulk]]
            [ctia.schemas.core :refer [NewBundle TempIDs]]
            [ctia.store :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]
            [ctim.domain.id :as id]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [ctia.domain.entities :as ent]
            [clojure.string :as string]))

(s/defschema EntityImportResult
  (st/optional-keys
   {:id s/Str
    :original_id s/Str
    :action (s/enum "keep" "create")
    :type s/Keyword
    :external_id s/Str
    :error s/Str}))

(s/defschema EntityImportData
  "Data structure used to keep a link between
   the transient ID, the external_id and the ID of an entity
   during the import process."
  (st/merge
   EntityImportResult
   (st/optional-keys
    {:new-entity s/Any
     :old-entity s/Any})))

(s/defschema BundleImportData {s/Keyword [EntityImportData]})

(s/defschema BundleImportResult
  {:results [EntityImportResult]})

(defn list-fn
  "Returns the list function for a given entity type"
  [entity-type]
  (case entity-type
    :actor          list-actors
    :attack-pattern list-attack-patterns
    :campaign       list-campaigns
    :coa            list-coas
    :data-table     list-data-tables
    :exploit-target list-exploit-targets
    :feedback       list-feedback
    :incident       list-incidents
    :indicator      list-indicators
    :judgement      list-judgements
    :malware        list-malwares
    :relationship   list-relationships
    :sighting       list-sightings
    :tool           list-tools))

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
  (->> external-ids
       (filter #(str/starts-with? % key-prefix))))

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
    (cond-> {:action "create"
             :new-entity entity
             :type entity-type}
      (transient-id? id) (assoc :original_id id)
      external_id (assoc :external_id external_id)
      (not external_id) (assoc :error "No valid external ID has been provided"))))

(defn find-by-external-ids
  [import-data entity-type identity-map]
  (let [external-ids (remove nil? (map :external_id import-data))]
    (log/debugf "Searching %s matching these external_ids %s"
                entity-type
                (pr-str external-ids))
    (if (seq external-ids)
      (->> (read-store entity-type (list-fn entity-type)
                       {:external_ids external-ids}
                       identity-map
                       {:limit (count external-ids)})
           :data)
      [])))

(defn by-external-id
  "Index entities by external_id

   Ex:
   {{:external_id \"ctia-1\"} {:external_id \"ctia-1\"
                               :entity {...}}
    {:external_id \"ctia-2\"} {:external_id \"ctia-2\"
                               :entity {...}}}"
  [entities]
  (debug "entities" entities)
  (let [entity-with-external-id
        (reduce (fn [acc {:keys [external_ids] :as entity}]
                  (set/union acc
                             (set (map (fn [external_id]
                                         {:external_id external_id
                                          :entity entity})
                                       external_ids))))
                #{}
                entities)]
    (set/index entity-with-external-id [:external_id])))

(s/defn entities-import-data->tempids :- TempIDs
  "Get a mapping table between orignal IDs and real IDs"
  [import-data :- [EntityImportData]]
  (->> import-data
       (map (fn [{:keys [original_id id]}]
              (when (and original_id id)
                [original_id id])))
       (remove nil?)
       (into {})))

(def entities-keys (map :k (keys Bulk)))

(defn map-kv
  "Apply a function to all entity collections within a map
  of entities indexed by entity type."
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

(s/defn with-existing-entities :- [EntityImportData]
  "Add existing entities to the import data map.
   If more than one old entity is linked to an external id,
   an error is reported."
  [import-data entity-type identity-map]
  (let [entities-by-external-id
        (-> (find-by-external-ids import-data
                                  entity-type
                                  identity-map)
            by-external-id)]
    (map (fn [{:keys [external_id]
               entity-type :type
               :as entity-data}]
           (if external_id
             (let [with-long-id-fn (bulk/with-long-id-fn entity-type)
                   old-entities
                   (get entities-by-external-id {:external_id external_id})
                   old-entity (some-> old-entities
                                      first
                                      :entity
                                      with-long-id-fn
                                      ent/un-store)
                   num-old-entities (count old-entities)]
               (cond-> entity-data
                 ;; only one entity linked to the external ID
                 (and old-entity
                      (= num-old-entities 1)) (assoc :action "keep"
                                                     ;;:old-entity old-entity
                                                     :id (:id old-entity))
                 ;; more than one entity linked to the external ID
                 (> num-old-entities 1)
                 (assoc :error
                        (format
                         (str "More than one entity is "
                              "linked to the external id %s (%s)")
                         external_id
                         (pr-str (map :id old-entities))))))
             entity-data))
         import-data)))

(s/defn prepare-import :- BundleImportData
  "Prepares the import data by searching all existing
   entities based on their external IDs. Only new entities
   will be imported"
  [bundle-entities
   external-key-prefixes
   identity-map]
  (map-kv (fn [k v]
            (let [entity-type (singular k)]
              (-> v
                  (init-import-data entity-type external-key-prefixes)
                  (with-existing-entities entity-type identity-map))))
          bundle-entities))

(s/defn prepare-bulk
  "Creates the bulk data structure with all entities to create."
  [bundle-import-data :- BundleImportData]
  (map-kv (fn [k v]
            (->> v
                 (map (fn [{:keys [new-entity error action]}]
                        ;; Add only new entities without error
                        (when (and (= action "create")
                                   (not error))
                          new-entity)))
                 (remove nil?)))
          bundle-import-data))

(s/defn build-response :- BundleImportResult
  "Builds response from the bulk result"
  [import-data :- BundleImportData
   tempids :- (s/maybe TempIDs)]
  {:results
   (map (fn [{:keys [original_id] :as entity-data}]
          (let [id (get tempids original_id)]
            (cond-> (dissoc entity-data :new-entity :old-entity)
              id (assoc :id id))))
        (apply concat (vals import-data)))})

(s/defn import-bundle :- BundleImportResult
  [bundle :- NewBundle
   external-key-prefixes :- (s/maybe s/Str)
   login]
  (let [bundle-entities (select-keys bundle entities-keys)
        bundle-import-data (prepare-import bundle-entities
                                           external-key-prefixes
                                           login)
        bulk (debug "bulk" (prepare-bulk bundle-import-data))
        tempids (->> bundle-import-data
                     (map (fn [[_ entities-import-data]]
                            (entities-import-data->tempids entities-import-data)))
                     (reduce into {}))
        {all-tempids :tempids} (debug "bulk result" (bulk/create-bulk bulk tempids login))]
    (build-response bundle-import-data all-tempids)))

(defroutes bundle-routes
  (context "/bundle" []
           :tags ["Bundle"]
           (POST "/import" []
                 :return BundleImportResult
                 :body [bundle NewBundle {:description "a Bundle to import"}]
                 :query-params
                 [{external-key-prefixes
                   :- (describe s/Str "Comma separated list of external key prefixes")
                   nil}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "POST many new entities using a single HTTP call"
                 :identity login
                 :identity-map identity-map
                 :capabilities #{:create-actor
                                 :create-attack-pattern
                                 :create-campaign
                                 :create-coa
                                 :create-data-table
                                 :create-exploit-target
                                 :create-feedback
                                 :create-incident
                                 :create-indicator
                                 :create-judgement
                                 :create-malware
                                 :create-relationship
                                 :create-sighting
                                 :create-tool
                                 :import-bundle}
                 (if (> (bulk/bulk-size bundle)
                        (bulk/get-bulk-max-size))
                   (bad-request (str "Bundle max nb of entities: " (bulk/get-bulk-max-size)))
                   (ok (import-bundle bundle external-key-prefixes login))))))
