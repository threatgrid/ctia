(ns ctia.entity.incident.graphql-schemas
  (:require [ctia.entity.feedback.graphql-schemas :as feedback]
            [ctia.entity.incident.schemas :refer [incident-fields]]
            [ctia.entity.relationship.graphql-schemas :as relationship-graphql]
            [ctia.schemas.graphql.flanders :as flanders]
            [ctia.schemas.graphql.helpers :as g]
            [ctia.schemas.graphql.ownership :as go]
            [ctia.schemas.graphql.pagination :as pagination]
            [ctia.schemas.graphql.sorting :as graphql-sorting]
            [ctim.schemas.incident :refer [Incident]]
            [flanders.utils :as fu]))

(def IncidentType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all Incident)
         {})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship-graphql/relatable-entity-fields
            go/graphql-ownership-fields))))

(def IncidentConnectionType
  (pagination/new-connection IncidentType))

(def incident-order-arg
  (graphql-sorting/order-by-arg
   "IncidentOrder"
   "incidents"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              incident-fields))))
