(ns ctia.incident.routes
  (:require [ctia.domain.entities :refer [realize-incident]]
            [ctia.schemas.sorting :as sorting]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas.core
             :refer
             [Incident NewIncident PartialIncident PartialIncidentList]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def incident-sort-fields
  (apply s/enum sorting/incident-sort-fields))

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
    (s/optional-key :reporter) s/Str
    (s/optional-key :coordinator) s/Str
    (s/optional-key :victim) s/Str
    (s/optional-key :security_compromise) s/Str
    (s/optional-key :discovery_method) s/Str
    (s/optional-key :contact) s/Str
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
    :external-id-q-params IncidentByExternalIdQueryParams
    :search-q-params IncidentSearchParams
    :new-spec :new-incident/map
    :realize-fn realize-incident
    :get-capabilities :read-incident
    :post-capabilities :create-incident
    :put-capabilities :create-incident
    :delete-capabilities :delete-incident
    :search-capabilities :search-incident
    :external-id-capabilities #{:read-incident :external-id}}))
