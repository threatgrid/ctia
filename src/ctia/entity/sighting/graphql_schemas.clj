(ns ctia.entity.sighting.graphql-schemas
  (:require [ctia.entity.feedback.graphql-schemas :as feedback]
            [ctia.entity.relationship.graphql-schemas :as relationship]
            [ctia.entity.sighting.schemas :as ss]
            [ctia.schemas.graphql
             [flanders :as flanders]
             [helpers :as g]
             [refs :as refs]
             [sorting :as sorting]
             [pagination :as pagination]]
            [ctim.schemas.sighting :as ctim-sighting]
            [flanders.utils :as fu]))

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
              ss/sighting-fields))))

(def SightingConnectionType
  (pagination/new-connection SightingType))
