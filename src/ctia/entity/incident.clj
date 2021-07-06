(ns ctia.entity.incident
  (:require
   [clj-momo.lib.clj-time.core :as time]
   [ctia.domain.entities
    :refer [default-realize-fn un-store with-long-id]]
   [ctia.entity.feedback.graphql-schemas :as feedback]
   [ctia.entity.relationship.graphql-schemas :as relationship-graphql]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.lib.compojure.api.core :refer [POST routes]]
   [ctia.schemas.core :refer [APIHandlerServices def-acl-schema def-stored-schema]]
   [ctia.schemas.graphql.flanders :as flanders]
   [ctia.schemas.graphql.helpers :as g]
   [ctia.schemas.graphql.ownership :as go]
   [ctia.schemas.graphql.pagination :as pagination]
   [ctia.schemas.graphql.sorting :as graphql-sorting]
   [ctia.schemas.sorting :refer [default-entity-sort-fields describable-entity-sort-fields sourcable-entity-sort-fields]]
   [ctia.store :refer [read-record update-record]]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
   [ctim.schemas.incident :as is]
   [ctim.schemas.vocabularies :as vocs]
   [flanders.schema :as fs]
   [flanders.utils :as fu]
   [ring.swagger.schema :refer [describe]]
   [ring.util.http-response :refer [not-found ok]]
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
  Incident)

(s/defschema PartialNewIncident
  (st/optional-keys-schema NewIncident))

(s/defschema PartialStoredIncident
  (st/optional-keys-schema StoredIncident))

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

(s/defn incident-additional-routes [{{:keys [get-store]} :StoreService
                                     :as services} :- APIHandlerServices]
  (routes
    (let [capabilities :create-incident]
      (POST "/:id/status" []
            :return Incident
            :body [update IncidentStatusUpdate
                   {:description "an Incident Status Update"}]
            :summary "Update an Incident Status"
            :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
            :path-params [id :- s/Str]
            :description (routes.common/capabilities->description capabilities)
            :capabilities :create-incident
            :auth-identity identity
            :identity-map identity-map
            (let [status-update (make-status-update update)]
              (if-let [updated
                       (un-store
                        (flows/patch-flow
                         :services services
                         :get-fn #(-> (get-store :incident)
                                      (read-record
                                        %
                                        identity-map
                                        {}))
                         :realize-fn realize-incident
                         :update-fn #(-> (get-store :incident)
                                         (update-record
                                           (:id %)
                                           %
                                           identity-map
                                           (routes.common/wait_for->refresh wait_for)))
                         :long-id-fn #(with-long-id % services)
                         :entity-type :incident
                         :entity-id id
                         :identity identity
                         :patch-operation :replace
                         :partial-entity status-update
                         :spec :new-incident/map))]
                (ok updated)
                (not-found)))))))

(def incident-mapping
  {"incident"
   {:dynamic false
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
      :intended_effect em/token
      :assignees em/token
      :promotion_method em/token})}})

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
           :intended_effect
           :assignees
           :promotion_method]))

(def incident-sort-fields
  (apply s/enum incident-fields))

(def incident-enumerable-fields
  [:source
   :confidence
   :status
   :discovery_method
   :assignees])

(def incident-histogram-fields
  [:timestamp
   :incident_time.opened
   :incident_time.discovered
   :incident_time.reported
   :incident_time.remediated
   :incident_time.closed
   :incident_time.rejected])

(s/defschema IncidentFieldsParam
  {(s/optional-key :fields) [incident-sort-fields]})

(s/defschema IncidentSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   IncidentFieldsParam
   (st/optional-keys
    {:confidence       s/Str
     :status           s/Str
     :discovery_method s/Str
     :intended_effect  s/Str
     :categories       s/Str
     :sort_by          incident-sort-fields
     :assignees        s/Str
     :promotion_method s/Str})))

(def IncidentGetParams IncidentFieldsParam)

(s/defschema IncidentByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   IncidentFieldsParam))

(s/defn incident-routes [services :- APIHandlerServices]
  (routes
   (incident-additional-routes services)
   (services->entity-crud-routes
    services
    {:entity                   :incident
     :new-schema               NewIncident
     :entity-schema            Incident
     :get-schema               PartialIncident
     :get-params               IncidentGetParams
     :list-schema              PartialIncidentList
     :search-schema            PartialIncidentList
     :patch-schema             PartialNewIncident
     :external-id-q-params     IncidentByExternalIdQueryParams
     :search-q-params          IncidentSearchParams
     :new-spec                 :new-incident/map
     :can-patch?               true
     :can-aggregate?           true
     :realize-fn               realize-incident
     :get-capabilities         :read-incident
     :post-capabilities        :create-incident
     :put-capabilities         :create-incident
     :patch-capabilities       :create-incident
     :delete-capabilities      :delete-incident
     :search-capabilities      :search-incident
     :external-id-capabilities :read-incident
     :histogram-fields         incident-histogram-fields
     :enumerable-fields        incident-enumerable-fields
     :searchable-fields        (routes.common/searchable-fields
                                {:fields incident-fields
                                 :ignore [:incident_time.remediated
                                          :incident_time.closed
                                          :incident_time.discovered
                                          :incident_time.reported
                                          :incident_time.opened
                                          :incident_time.rejected]})})))

(def IncidentType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all is/Incident)
         {})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship-graphql/relatable-entity-fields
            go/graphql-ownership-fields))))

(def incident-order-arg
  (graphql-sorting/order-by-arg
   "IncidentOrder"
   "incidents"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              incident-fields))))

(def IncidentConnectionType
  (pagination/new-connection IncidentType))

(def capabilities
  #{:create-incident
    :read-incident
    :delete-incident
    :search-incident})

(def incident-entity
  {:route-context         "/incident"
   :tags                  ["Incident"]
   :entity                :incident
   :plural                :incidents
   :new-spec              :new-incident/map
   :schema                Incident
   :partial-schema        PartialIncident
   :partial-list-schema   PartialIncidentList
   :new-schema            NewIncident
   :stored-schema         StoredIncident
   :partial-stored-schema PartialStoredIncident
   :realize-fn            realize-incident
   :es-store              ->IncidentStore
   :es-mapping            incident-mapping
   :services->routes      (routes.common/reloadable-function incident-routes)
   :capabilities          capabilities
   :fields                incident-fields
   :sort-fields           incident-fields})
