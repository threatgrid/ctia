(ns ctia.entity.asset-properties.graphql-schemas
  (:require
   [ctia.entity.asset-properties :as asset-properties]
   [ctia.entity.feedback.graphql-schemas :as feedback]
   [ctia.entity.relationship.graphql-schemas :as relationship]
   [ctia.schemas.graphql.flanders :as flanders]
   [ctia.schemas.graphql.helpers :as g]
   [ctia.schemas.graphql.ownership :as go]
   [ctia.schemas.graphql.pagination :as pagination]
   [ctia.schemas.graphql.sorting :as sorting]
   [ctim.schemas.asset-properties :as asset-properties-schema]
   [flanders.utils :as fu]))

(def AssetPropertiesType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all asset-properties-schema/AssetProperties)
         {})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship/relatable-entity-fields
            go/graphql-ownership-fields))))

(def asset-properties-order-arg
  (sorting/order-by-arg
   "AssetPropertiesOrder"
   "asset-properties"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              asset-properties/asset-properties-fields))))

(def AssetPropertiesConnectionType
  (pagination/new-connection AssetPropertiesType))
