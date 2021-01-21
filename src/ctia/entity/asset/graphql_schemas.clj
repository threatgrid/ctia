(ns ctia.entity.asset.graphql-schemas
  (:require [ctia.entity.asset :as asset]
            [ctia.entity.feedback.graphql-schemas :as feedback]
            [ctia.entity.relationship.graphql-schemas :as relationship]
            [ctia.schemas.graphql.flanders :as flanders]
            [ctia.schemas.graphql.helpers :as g]
            [ctia.schemas.graphql.ownership :as go]
            [ctia.schemas.graphql.pagination :as pagination]
            [ctia.schemas.graphql.sorting :as sorting]
            [ctim.schemas.asset :as asset-schema]
            [flanders.utils :as fu]))

(def AssetType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all asset-schema/Asset)
         {})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship/relatable-entity-fields
            go/graphql-ownership-fields))))

(def asset-order-arg
  (sorting/order-by-arg
   "AssetOrder"
   "assets"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              asset/asset-fields))))

(def AssetConnectionType
  (pagination/new-connection AssetType))
