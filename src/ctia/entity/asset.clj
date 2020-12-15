(ns ctia.entity.asset
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.schemas.core :refer [APIHandlerServices def-acl-schema def-stored-schema]]
            [ctia.schemas.utils :as csu]
            [ctia.schemas.sorting :as sorting]
            [ctia.stores.es.mapping :as em]
            [ctia.stores.es.store :refer [def-es-store]]
            [ctim.schemas.asset :as asset-schema]
            [schema-tools.core :as st]
            [ctia.http.routes.crud :refer [services->entity-crud-routes]]
            [ctia.http.routes.common :as routes.common]
            [flanders.utils :as fu]
            [schema.core :as s]))

(def-acl-schema Asset
  asset-schema/Asset
  "asset")

(def-acl-schema PartialAsset
  (fu/optionalize-all asset-schema/Asset)
  "partial-asset")

(s/defschema PartialAssetList
  [PartialAsset])

(def-acl-schema NewAsset
  asset-schema/NewAsset
  "new-asset")

(def-stored-schema StoredAsset Asset)

(s/defschema PartialStoredAsset
  (csu/optional-keys-schema StoredAsset))

(def realize-asset (default-realize-fn "asset" NewAsset StoredAsset))

(def asset-mapping
  {"asset"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:asset_type em/token
      :valid_time em/valid-time})}})

(def-es-store AssetStore :asset StoredAsset PartialStoredAsset)

(def asset-fields
  (concat sorting/default-entity-sort-fields
          [:asset_type]))

(def asset-sort-fields
  (apply s/enum asset-fields))

(s/defschema AssetFieldsParam
  {(s/optional-key :fields) [asset-sort-fields]})

(s/defschema AssetByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   AssetFieldsParam))

(def asset-enumerable-fields
  [:asset_type])

(def AssetGetParams AssetFieldsParam)

(s/defschema AssetSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   AssetFieldsParam
   (st/optional-keys
    {:query           s/Str
     :asset_type      s/Str
     :sort_by         asset-sort-fields})))

(def asset-histogram-fields
  [:timestamp
   :valid_time.start_time
   :valid_time.end_time])

(s/defn asset-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :asset
    :new-schema               NewAsset
    :entity-schema            Asset
    :get-schema               PartialAsset
    :get-params               AssetGetParams
    :list-schema              PartialAssetList
    :search-schema            PartialAssetList
    :external-id-q-params     AssetByExternalIdQueryParams
    :search-q-params          AssetSearchParams
    :new-spec                 :new-asset/map
    :realize-fn               realize-asset
    :get-capabilities         :read-asset
    :post-capabilities        :create-asset
    :put-capabilities         :create-asset
    :delete-capabilities      :delete-asset
    :search-capabilities      :search-asset
    :external-id-capabilities :read-asset
    :can-aggregate?           true
    :histogram-fields         asset-histogram-fields
    :enumerable-fields        asset-enumerable-fields
    }))

(def capabilities
  #{:create-asset
    :read-asset
    :delete-asset
    :search-asset})

(def asset-entity
  {:route-context         "/asset"
   :tags                  ["Asset"]
   :entity                :asset
   :plural                :assets
   :new-spec              :new-asset/map
   :schema                Asset
   :partial-schema        PartialAsset
   :partial-list-schema   PartialAssetList
   :new-schema            NewAsset
   :stored-schema         StoredAsset
   :partial-stored-schema PartialStoredAsset
   :realize-fn            realize-asset
   :es-store              ->AssetStore
   :es-mapping            asset-mapping
   :services->routes      (routes.common/reloadable-function
                            asset-routes)
   :capabilities          capabilities})
