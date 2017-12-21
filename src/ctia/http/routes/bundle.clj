(ns ctia.http.routes.bundle
  (:require [compojure.api.sweet :refer :all]
            [ctia.http.routes.bulk :as bulk]
            [ctia.lib.keyword :refer [singular]]
            [ctia.properties :refer [properties]]
            [ctia.schemas.core :refer [NewBundle]]
            [ctia.store :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]
            [ctim.domain.id :as id]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.tools.logging :as log]))

(s/defschema EntityImportResult
  (st/optional-keys
   {:id s/Str
    :tempid s/Str
    :action (s/enum "update" "create")
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
  (st/optional-keys
   {:actors          [EntityImportResult]
    :attack-patterns [EntityImportResult]
    :campaigns       [EntityImportResult]
    :coas            [EntityImportResult]
    :data-tables     [EntityImportResult]
    :exploit-targets [EntityImportResult]
    :feedbacks       [EntityImportResult]
    :incidents       [EntityImportResult]
    :indicators      [EntityImportResult]
    :judgements      [EntityImportResult]
    :malwares        [EntityImportResult]
    :relationships   [EntityImportResult]
    :sightings       [EntityImportResult]
    :tools           [EntityImportResult]}))

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

(def default-external-id-prefix "ctia-")

(s/defn valid-external-id :- (s/maybe s/Str)
  "Returns the external ID that can be used to check whether an entity has
  already been imported or not."
  [external-ids]
  (let [valid-prefix (get-in @properties
                             [:ctia :http :bundle :external-id-prefix]
                             default-external-id-prefix)]
    (->> external-ids
         (filter #(str/starts-with? % valid-prefix))
         first)))

(s/defn entity->import-data :- EntityImportData
  "Initializes an result map with an entity of the bundle."
  [{:keys [id external_ids] :as entity}]
  (let [external_id (valid-external-id external_ids)]
    (cond-> {:action "create"
             :new-entity entity}
      (transient-id? id) (assoc :tempid id)
      external_id (assoc :external_id external_id))))

(s/defn init-import-data :- [EntityImportData]
  [entities]
  (map entity->import-data entities))

(defn find-by-external-ids
  [external_ids entity-type identity-map]
  (->> (read-store entity-type (list-fn entity-type)
                   {:external_ids external_ids}
                   identity-map
                   {:limit (count external_ids)})
       :data))

(defn by-external-id
  "Index entities by external_id"
  [entities]
  (let [entity-with-external-id
        (reduce (fn [acc {:keys [external_ids] :as entity}]
                  (set/union acc
                             (set (map (fn [external_id]
                                         {:external_id external_id
                                          :entity entity})))))
                #{}
                entities)]
    (set/index entity-with-external-id [:external_id])))

(s/defn with-old-entities :-  [EntityImportData]
  "Add old entities to the import data map.
   If more than one old entity is linked to an external id,
   an error is reported."
  [entities-by-external-id
   import-data :- [EntityImportData]]
  (map (fn [{:keys [external_id] :as entity-data}]
         (if external_id
           (let [[old-entity old-entity-2 :as old-entities]
                 (get entities-by-external-id external_id)]
             (cond-> entity-data
               ;; only one entity linked to the external ID
               (and old-entity
                    (not old-entity-2)) (assoc :old-entity old-entity
                                               :id (:id old-entity))
               ;; more than one entity linked to the external ID
               old-entity-2 (assoc :error
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
   identity-map]
  (let [import-data (init-import-data entities)
        entities-by-external-id
        (-> (map :external_id import-data)
            (find-by-external-ids entity-type
                                  identity-map)
            by-external-id)]
    (map merge-entity
         (with-old-entities
           import-data
           entities-by-external-id))))

(s/defn import-data->tempids
  [import-data :- [EntityImportData]]
  (->> import-data
       (map (fn [{:keys [tempid id]}]
              (when tempid
                [tempid id])))
       (remove nil?)
       (into {})))

(s/defn import-bundle :- BundleImportResult
  [bundle :- NewBundle
   identity-map]
  (let [bundle-import-data
        (reduce-kv (fn [m k v]
                     (assoc m k (merge-entities v (singular k) identity-map)))
                   bundle)
        tempids (->> bundle-import-data
                     (map import-data->tempids)
                     (reduce into {}))
        bulk
        (reduce-kv (fn [m k v]
                     (assoc m k (->> (map (fn [{:keys [new-entity error]}]
                                            (when-not error
                                              new-entity))
                                          v)
                                     (remove nil?))))
                   bundle)
        {all-tempids :tempids} (bulk/create-bulk bulk tempids identity-map)]
    (reduce-kv (fn [m k v]
                 (assoc m k (map (fn [{:keys [tempid] :as entity-data}]
                                   (let [id (get tempids tempid)]
                                     (cond-> (dissoc entity-data :new-entity :old-entity)
                                       id (assoc :id id))))
                                 v)))
               bundle-import-data)))

(defroutes bundle-routes
  (context "/bundle" []
           :tags ["Bundle"]
           (POST "/import" []
                 :return BundleImportResult
                 :body [bundle NewBundle {:description "a Bundle to import"}]
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
                   (ok "Imported")))))
