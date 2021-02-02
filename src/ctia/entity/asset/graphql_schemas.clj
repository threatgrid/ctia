(ns ctia.entity.asset.graphql-schemas
  (:require
   [ctia.entity.asset :as asset]
   [ctia.entity.asset-mapping.graphql-schemas :refer [asset-mapping-order-arg]]
   [ctia.entity.asset-properties.graphql-schemas :refer [asset-properties-order-arg]]
   [ctia.entity.feedback.graphql-schemas :as feedback]
   [ctia.entity.relationship.graphql-schemas :as relationship]
   [ctia.schemas.graphql.flanders :as flanders]
   [ctia.schemas.graphql.helpers :as g]
   [ctia.schemas.graphql.ownership :as go]
   [ctia.schemas.graphql.pagination :as pagination]
   [ctia.schemas.graphql.resolvers :as resolvers]
   [ctia.schemas.graphql.sorting :as sorting]
   [ctim.schemas.asset :as asset-schema]
   [ctim.schemas.asset-mapping :as ctim-asset-mapping]
   [ctim.schemas.asset-properties :as ctim-asset-properties]
   [flanders.utils :as fu]))

(def AssetMappingRefType
  (let [{:keys [fields name description]}
        (flanders/->graphql (fu/optionalize-all ctim-asset-mapping/AssetMapping))]
    (g/new-object name description []
                  (merge fields
                         go/graphql-ownership-fields))))

(def AssetRefAssetMappingConnectionType
  (pagination/new-connection AssetMappingRefType))

(def AssetPropertiesRefType
  (let [{:keys [fields name description]}
        (flanders/->graphql (fu/optionalize-all ctim-asset-properties/AssetProperties))]
    (g/new-object name description []
                  (merge fields
                         go/graphql-ownership-fields))))

(def AssetRefAssetPropertiesConnectionType
  (pagination/new-connection AssetPropertiesRefType))

(def asset-ref-connection-field
  {:asset_mappings
   {:type        AssetRefAssetMappingConnectionType
    :description "Related AssetMappings"
    :args        (into pagination/connection-arguments
                       asset-mapping-order-arg)
    :resolve     (fn [context args field-selection src]
                   (resolvers/search-asset-mappings-by-asset-ref
                    (:id src)
                    context
                    args
                    field-selection))}
   :asset_properties
   {:type        AssetRefAssetPropertiesConnectionType
    :description "Related AssetMappings"
    :args        (into pagination/connection-arguments
                       asset-properties-order-arg)
    :resolve     (fn [context args field-selection src]
                   (resolvers/search-asset-properties-by-asset-ref
                    (:id src)
                    context
                    args
                    field-selection))}})

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
            asset-ref-connection-field
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

