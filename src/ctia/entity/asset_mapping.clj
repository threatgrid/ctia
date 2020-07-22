(ns ctia.entity.asset-mapping
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.schemas.core :refer [def-acl-schema def-stored-schema]]
            [ctia.schemas.utils :as csu]
            [ctia.schemas.sorting :as sorting]
            [ctia.stores.es.mapping :as em]
            [ctia.stores.es.store :refer [def-es-store]]
            [ctim.schemas.asset :as asset-schema]
            [schema-tools.core :as st]
            [ctia.http.routes.crud :refer [entity-crud-routes]]
            [ctia.http.routes.common :as routes.common]
            [flanders.utils :as fu]
            [schema.core :as s]))

(def-acl-schema AssetMapping
  asset-schema/AssetMapping
  "asset-mapping")

(def-acl-schema PartialAssetMapping
  (fu/optionalize-all asset-schema/AssetMapping)
  "partial-asset-mapping")

(s/defschema PartialAssetMappingList
  [PartialAssetMapping])

(def-acl-schema NewAssetMapping
  asset-schema/NewAssetMapping
  "new-asset-mapping")

(def-stored-schema StoredAssetMapping AssetMapping)

(s/defschema PartialStoredAssetMapping
  (csu/optional-keys-schema StoredAssetMapping))

(def realize-asset-mapping
  (default-realize-fn "asset-mapping" NewAssetMapping StoredAssetMapping))

(def asset-mapping-mapping
  {"asset-mapping"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:identity    em/tg-identity
      :valid_time  em/valid-time
      :confidence  em/token
      :specificity em/token
      :stability   em/token
      :observable  em/token
      :asset_ref   em/token})}})

(def-es-store AssetMappingStore :asset-mapping StoredAssetMapping PartialStoredAssetMapping)

(def asset-mapping-fields
  (concat sorting/default-entity-sort-fields
          [:confidence
           :specificity
           :stability
           :observable
           :asset_ref]))

(def asset-mapping-sort-fields
  (apply s/enum asset-mapping-fields))

(s/defschema AssetMappingFieldsParam
  {(s/optional-key :fields) [asset-mapping-sort-fields]})

(s/defschema AssetMappingByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   AssetMappingFieldsParam))

(def asset-mapping-enumerable-fields
  [:confidence
   :specificity
   :stability])

(def AssetMappingGetParams AssetMappingFieldsParam)

(s/defschema AssetMappingSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   AssetMappingFieldsParam
   (st/optional-keys
    {:query           s/Str
     :sort_by         asset-mapping-sort-fields})))

(def asset-mapping-histogram-fields
  [:timestamp
   :valid_time.start_time
   :valid_time.end_time])

(def asset-mapping-routes
  (entity-crud-routes
   {:entity                   :asset-mapping
    :new-schema               NewAssetMapping
    :entity-schema            AssetMapping
    :get-schema               PartialAssetMapping
    :get-params               AssetMappingGetParams
    :list-schema              PartialAssetMappingList
    :search-schema            PartialAssetMappingList
    :external-id-q-params     AssetMappingGetParams
    :search-q-params          AssetMappingSearchParams
    :new-spec                 :new-asset-mapping/map
    :realize-fn               realize-asset-mapping
    :get-capabilities         :read-asset-mapping
    :post-capabilities        :create-asset-mapping
    :put-capabilities         :create-asset-mapping
    :delete-capabilities      :delete-asset-mapping
    :search-capabilities      :search-asset-mapping
    :external-id-capabilities :read-asset-mapping
    :can-aggregate?           true
    :histogram-fields         asset-mapping-histogram-fields
    :enumerable-fields        asset-mapping-enumerable-fields
    }))

(def capabilities
  #{:create-asset-mapping
    :read-asset-mapping
    :delete-asset-mapping
    :search-asset-mapping})

(def asset-mapping-entity
  {:route-context         "/asset-mapping"
   :tags                  ["Asset Mapping"]
   :entity                :asset-mapping
   :plural                :asset-mappings
   :new-spec              :new-asset-mapping/map
   :schema                AssetMapping
   :partial-schema        PartialAssetMapping
   :partial-list-schema   PartialAssetMappingList
   :new-schema            NewAssetMapping
   :stored-schema         StoredAssetMapping
   :partial-stored-schema PartialStoredAssetMapping
   :realize-fn            realize-asset-mapping
   :es-store              ->AssetMappingStore
   :es-mapping            asset-mapping-mapping
   :routes                asset-mapping-routes
   :capabilities          capabilities
   })
