(ns ctia.entity.asset-mapping.graphql-schemas
  (:require
   [ctia.entity.asset-mapping :as asset-mapping]
   [ctia.entity.feedback.graphql-schemas :as feedback]
   [ctia.entity.relationship.graphql-schemas :as relationship]
   [ctia.schemas.graphql.flanders :as flanders]
   [ctia.schemas.graphql.helpers :as g]
   [ctia.schemas.graphql.ownership :as go]
   [ctia.schemas.graphql.pagination :as pagination]
   [ctia.schemas.graphql.sorting :as sorting]
   [ctim.schemas.asset-mapping :as asset-mapping-schema]
   [flanders.utils :as fu]))

(def AssetMappingType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all asset-mapping-schema/AssetMapping)
         {})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship/relatable-entity-fields
            go/graphql-ownership-fields))))

(def asset-mapping-order-arg
  (sorting/order-by-arg
   "AssetMappingOrder"
   "asset-mappings"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              asset-mapping/asset-mapping-fields))))

(def AssetMappingConnectionType
  (pagination/new-connection AssetMappingType))
