(ns ctia.sighting.graphql.schemas
  (:require
   [flanders.utils :as fu]
   [ctia.feedback.graphql.schemas :as feedback]
   [ctia.relationship.graphql.schemas :as relationship]
   [ctia.schemas.graphql
    [flanders :as flanders]
    [helpers :as g]
    [pagination :as pagination]
    [refs :as refs]
    [sorting :as sorting]]
   [ctia.schemas.sorting :as sort-fields]
   [ctim.schemas.sighting :as ctim-sighting]))

(def SightingType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all ctim-sighting/Sighting)
         {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship/relatable-entity-fields))))

(def sighting-order-arg
  (sorting/order-by-arg
   "SightingOrder"
   "sightings"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              sort-fields/sighting-sort-fields))))

(def SightingConnectionType
  (pagination/new-connection SightingType))
