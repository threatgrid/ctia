(ns ctia.http.routes.bundle
  (:require [compojure.api.sweet :refer :all]
            [ctia.http.routes.bulk :as bulk]
            [ctia.lib.keyword :refer [singular]]
            [ctia.properties :refer [properties]]
            [ctia.schemas.bulk :refer [Bulk]]
            [ctia.schemas.core :refer [NewBundle]]
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
    :action (s/enum "update" "create")
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

(defn parse-key-prefixes
  [s]
  (map string/trim
       (string/split s #",")))

(s/defn entity->import-data :- EntityImportData
  "Initializes an result map with an entity of the bundle."
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

(s/defn init-import-data :- [EntityImportData]
  [entities entity-type external-key-prefixes]
  (map #(entity->import-data % entity-type external-key-prefixes)
       entities))

(defn find-by-external-ids
  [external_ids entity-type identity-map]
  (log/debugf "Searching %s matching these external_ids %s"
              entity-type
              (pr-str external_ids))
  (if (seq external_ids)
    (->> (read-store entity-type (list-fn entity-type)
                     {:external_ids external_ids}
                     identity-map
                     {:limit (count external_ids)})
         :data)
    []))

(defn by-external-id
  "Index entities by external_id"
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

(s/defn with-old-entities :-  [EntityImportData]
  "Add old entities to the import data map.
   If more than one old entity is linked to an external id,
   an error is reported."
  [entities-by-external-id
   import-data :- [EntityImportData]]
  (debug "entities-by-external-id" entities-by-external-id)
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
                    (= num-old-entities 1)) (assoc :old-entity old-entity
                                                   :action "update"
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
       import-data))

(s/defn merge-entity :- EntityImportData
  [{:keys [id old-entity new-entity]
    :as entity-data} :- EntityImportData]
  (if old-entity
    (assoc entity-data
           :new-entity
           (into old-entity
                 (dissoc new-entity :id)))
    entity-data))

(s/defn merge-entities :- [EntityImportData]
  [entities
   entity-type
   external-key-prefixes :- (s/maybe s/Str)
   identity-map]
  (let [import-data (debug "initial-import-data"
                           (init-import-data entities
                                             entity-type
                                             external-key-prefixes))
        external-ids (remove nil? (map :external_id import-data))
        entities-by-external-id
        (-> external-ids
            (find-by-external-ids entity-type
                                  identity-map)
            by-external-id)]
    (debug "merged entities"
           (map merge-entity
                (with-old-entities
                  entities-by-external-id
                  import-data)))))

(s/defn import-data->tempids
  [import-data :- [EntityImportData]]
  (->> import-data
       (map (fn [{:keys [original_id id]}]
              (when (and original_id id)
                [original_id id])))
       (remove nil?)
       (into {})))

(def entities-keys (map :k (keys Bulk)))

(s/defn import-bundle :- BundleImportResult
  [bundle :- NewBundle
   external-key-prefixes :- (s/maybe s/Str)
   login]
  (let [bundle-entities (select-keys bundle entities-keys)
        bundle-import-data
        (reduce-kv (fn [m k v]
                     (assoc m k (merge-entities v
                                                (singular k)
                                                external-key-prefixes
                                                login)))
                   {}
                   bundle-entities)
        tempids (debug "tempids"
                       (->> bundle-import-data
                            (map (fn [[_ import-data]]
                                   (import-data->tempids import-data)))
                            (reduce into {})))
        bulk
        (debug "bulk" (reduce-kv (fn [m k v]
                                   (when-let [entities
                                              (seq
                                               (->> (map (fn [{:keys [new-entity error]}]
                                                           (when-not error
                                                             new-entity))
                                                         v)
                                                    (remove nil?)))]
                                     (assoc m k entities)))
                                 {}
                                 bundle-import-data))
        {all-tempids :tempids} (debug "bulk result" (bulk/create-bulk bulk tempids login))
        results (reduce-kv (fn [m k v]
                             (into m (map (fn [{:keys [original_id] :as entity-data}]
                                            (let [id (get all-tempids original_id)]
                                              (cond-> (dissoc entity-data :new-entity :old-entity)
                                                id (assoc :id id))))
                                          v)))
                           []
                           bundle-import-data)]
    {:results results}))

(defroutes bundle-routes
  (context "/bundle" []
           :tags ["Bundle"]
           (POST "/import" []
                 :return BundleImportResult
                 :body [bundle NewBundle {:description "a Bundle to import"}]
                 :query-params [{external-key-prefixes
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
