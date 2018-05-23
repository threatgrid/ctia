(ns ctia.entity.incident
  (:require
   [ctia.domain.entities :refer [default-realize-fn
                                 un-store
                                 with-long-id
                                 short-id->long-id]]
   [ctia.flows.crud :as flows]
   [ring.util.http-response :refer [ok not-found]]
   [clj-momo.lib.clj-time.core :as time]
   [compojure.api.sweet :refer [context POST routes]]
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.store :refer :all]
   [ctia.http.routes
    [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
    [crud :refer [entity-crud-routes]]]
   [ctia.schemas
    [core :refer [def-acl-schema def-stored-schema]]
    [sorting :refer [default-entity-sort-fields describable-entity-sort-fields sourcable-entity-sort-fields]]]
   [ctia.stores.es
    [mapping :as em]
    [store :refer [def-es-store]]]
   [ctim.schemas.incident :as is]
   [ctim.schemas.vocabularies :as vocs]
   [flanders.schema :as fs]
   [flanders.utils :as fu]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def incident-bundle-default-limit 1000)

(def-acl-schema Incident
  is/Incident
  "incident")

(def-acl-schema PartialIncident
  (fu/optionalize-all is/Incident)
  "partial-incident")

(s/defschema PartialIncidentList
  [PartialIncident])

(def-acl-schema NewIncident
  is/NewIncident
  "new-incident")

(def-stored-schema StoredIncident
  is/StoredIncident
  "stored-incident")

(def-stored-schema PartialNewIncident
  (fu/optionalize-all is/NewIncident)
  "partial-new-incident")

(def-stored-schema PartialStoredIncident
  (fu/optionalize-all is/StoredIncident)
  "partial-stored-incident")

(def realize-incident
  (default-realize-fn "incident" NewIncident StoredIncident))

(s/defschema IncidentStatus
  (fs/->schema vocs/Status))

(s/defschema IncidentStatusUpdate
  {:status IncidentStatus})

(defn make-status-update
  [{:keys [status]}]
  (let [t (time/internal-now)
        verb (case status
               "New" nil
               "Stalled" nil
               ("Containment Achieved"
                "Restoration Achieved") :remediated
               "Open" :opened
               "Rejected" :rejected
               "Closed" :closed
               "Incident Reported" :reported
               nil)]
    (cond-> {:status status}
      verb (assoc :incident_time {verb t}))))

#_(defn get-incident-entities-bundle
    "fetch all incident related entities, assemble them into a Bundle"
    [incident-id
     identity-map
     include_related_entities
     limit]
    (let [relationships
          (read-store :relationship list-records
                      {:target_ref (short-id->long-id incident-id)}
                      identity-map
                      {:limit limit})]
      relationships))

(def incident-operation-routes
  (routes
   (POST "/:id/status" []
         :return Incident
         :body [update IncidentStatusUpdate
                {:description "an Incident Status Update"}]
         :header-params [{Authorization :- (s/maybe s/Str) nil}]
         :summary "Update an Incident Status"
         :path-params [id :- s/Str]
         :capabilities :create-incident
         :auth-identity identity
         :identity-map identity-map
         (let [status-update (make-status-update update)]
           (if-let [updated
                    (un-store
                     (flows/patch-flow
                      :get-fn #(read-store :incident
                                           read-record
                                           %
                                           identity-map
                                           {})
                      :realize-fn realize-incident
                      :update-fn #(write-store :incident
                                               update-record
                                               (:id %)
                                               %
                                               identity-map)
                      :long-id-fn with-long-id
                      :entity-type :incident
                      :entity-id id
                      :identity identity
                      :patch-operation :replace
                      :partial-entity status-update
                      :spec :new-incident/map))]
             (ok updated)
             (not-found))))

   #_(GET "/:id/export" []
          :return Bundle
          :header-params [{Authorization :- (s/maybe s/Str) nil}]
          :summary "Get an Incident related entities as a Bundle"
          :query-params [{limit :- s/Num incident-bundle-default-limit}
                         {include_related_entities :- s/Bool false}]
          :path-params [id :- s/Str]
          :capabilities #{:read-actor
                          :read-attack-pattern
                          :read-campaign
                          :read-coa
                          :read-data-table
                          :read-exploit-target
                          :read-feedback
                          :read-incident
                          :read-indicator
                          :read-investigation
                          :read-judgement
                          :read-malware
                          :read-relationship
                          :read-casebook
                          :read-sighting
                          :read-tool}
          :auth-identity identity
          :identity-map identity-map
          (get-incident-entities-bundle id
                                        identity-map
                                        limit
                                        include_related_entities))))

(def incident-mapping
  {"incident"
   {:dynamic false
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:confidence em/token
      :status em/token
      :incident_time em/incident-time
      :categories em/token
      :discovery_method em/token
      :intended_effect em/token})}})

(def-es-store IncidentStore :incident StoredIncident PartialStoredIncident)

(def incident-fields
  (concat default-entity-sort-fields
          describable-entity-sort-fields
          sourcable-entity-sort-fields
          [:confidence
           :status
           :incident_time.opened
           :incident_time.discovered
           :incident_time.reported
           :incident_time.remediated
           :incident_time.closed
           :incident_time.rejected
           :discovery_method
           :intended_effect]))

(def incident-sort-fields
  (apply s/enum incident-fields))

(s/defschema IncidentFieldsParam
  {(s/optional-key :fields) [incident-sort-fields]})

(s/defschema IncidentSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   IncidentFieldsParam
   {:query s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :status) s/Str
    (s/optional-key :discovery_method) s/Str
    (s/optional-key :intended_effect) s/Str
    (s/optional-key :categories) s/Str
    (s/optional-key :sort_by) incident-sort-fields}))

(def IncidentGetParams IncidentFieldsParam)

(s/defschema IncidentByExternalIdQueryParams
  (st/merge
   PagingParams
   IncidentFieldsParam))

(def incident-routes
  (entity-crud-routes
   {:entity :incident
    :new-schema NewIncident
    :entity-schema Incident
    :get-schema PartialIncident
    :get-params IncidentGetParams
    :list-schema PartialIncidentList
    :search-schema PartialIncidentList
    :patch-schema PartialNewIncident
    :external-id-q-params IncidentByExternalIdQueryParams
    :search-q-params IncidentSearchParams
    :new-spec :new-incident/map
    :can-patch? true
    :realize-fn realize-incident
    :get-capabilities :read-incident
    :post-capabilities :create-incident
    :put-capabilities :create-incident
    :patch-capabilities :create-incident
    :delete-capabilities :delete-incident
    :search-capabilities :search-incident
    :external-id-capabilities #{:read-incident :external-id}}))

(def capabilities
  #{:create-incident
    :read-incident
    :delete-incident
    :search-incident})

(def incident-entity
  {:route-context "/incident"
   :tags ["Incident"]
   :entity :incident
   :plural :incidents
   :schema Incident
   :partial-schema PartialIncident
   :partial-list-schema PartialIncidentList
   :new-schema NewIncident
   :stored-schema StoredIncident
   :partial-stored-schema PartialStoredIncident
   :realize-fn realize-incident
   :es-store ->IncidentStore
   :es-mapping incident-mapping
   :routes incident-routes
   :capabilities capabilities
   :additional-routes incident-operation-routes})
