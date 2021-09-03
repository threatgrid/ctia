(ns ctia.entity.incident.schemas
  (:require [ctia.schemas.core :refer [def-acl-schema def-stored-schema]]
            [ctim.schemas.incident :as is]
            [ctim.schemas.vocabularies :as vocs]
            [ctia.http.routes.common :as routes.common]
            [flanders.schema :as fs]
            [flanders.utils :as fu]
            [ctia.schemas.sorting
             :refer
             [default-entity-sort-fields
              describable-entity-sort-fields
              sourcable-entity-sort-fields]]
            [schema-tools.core :as st]
            [schema.core :as s]))

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

(def-stored-schema StoredIncident Incident)

(s/defschema PartialNewIncident
  (st/optional-keys-schema NewIncident))

(s/defschema PartialStoredIncident
  (st/optional-keys-schema StoredIncident))


(s/defschema IncidentStatus
  (fs/->schema vocs/Status))

(s/defschema IncidentStatusUpdate
  {:status IncidentStatus})

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
  [:assignees
   :categories
   :confidence
   :discovery_method
   :intended_effect
   :promotion_method
   :source
   :status
   :title])

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
     :promotion_method s/Str
     :high_impact      s/Bool})))

(def IncidentGetParams IncidentFieldsParam)

(s/defschema IncidentByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   IncidentFieldsParam))
