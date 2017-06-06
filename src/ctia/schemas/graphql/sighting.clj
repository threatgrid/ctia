(ns ctia.schemas.graphql.sighting
  (:require [ctia.schemas.graphql
             [feedback :as feedback]
             [flanders :as flanders]
             [helpers :as g]
             [pagination :as pagination]
             [refs :as refs]
             [relationship :as relationship]]
            [ctim.schemas.sighting :as ctim-sighting]))

(def SightingType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         ctim-sighting/Sighting
         {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship/relatable-entity-fields))))

(def SightingConnectionType
  (pagination/new-connection SightingType))
