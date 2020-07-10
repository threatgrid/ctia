(ns ctia.entity.asset
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

(def-acl-schema Asset
  asset-schema/Asset
  "asset")

(def-acl-schema PartialAsset
  (fu/optionalize-all asset-schema/Asset)
  "partial-actor")

(s/defschema PartialAssetList
  [PartialAsset])

(def-acl-schema NewAsset
  asset-schema/NewAsset
  "new-actor")

(def-stored-schema StoredAsset Asset)

(s/defschema PartialStoredAsset
  (csu/optional-keys-schema StoredAsset))

(def realize-asset (default-realize-fn "asset" NewAsset StoredAsset))

(def asset-mapping
  {"asset"
   {:dynamic false
    :properties
    {:asset_type em/all_token}}})

(def-es-store AssetStore :asset StoredAsset PartialStoredAsset)

(def asset-fields
  (concat sorting/default-entity-sort-fields
          [:asset_type]))

(def asset-sort-fields
  (apply s/enum asset-fields))

(s/defschema AssetFieldsParam
  {(s/optional-key :fields) [asset-sort-fields]})

(s/defschema AssetFieldsParam
  {(s/optional-key :fields) [asset-sort-fields]})

(s/defschema AssetByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   AssetFieldsParam))

(def AssetGetParams AssetFieldsParam)

(def asset-routes
  (entity-crud-routes
   {:entity                   :asset
    :new-schema               NewAsset
    :entity-schema            Asset
    :get-schema               PartialAsset
    :get-params               AssetGetParams
    :list-schema              PartialAssetList
    :search-schema            PartialAssetList
    :external-id-q-params     AssetByExternalIdQueryParams
    ;; :search-q-params          ActorSearchParams
    ;; :new-spec                 :new-actor/map
    ;; :realize-fn               realize-actor
    ;; :get-capabilities         :read-actor
    ;; :post-capabilities        :create-actor
    ;; :put-capabilities         :create-actor
    ;; :delete-capabilities      :delete-actor
    ;; :search-capabilities      :search-actor
    ;; :external-id-capabilities :read-actor
    ;; :can-aggregate?           true
    ;; :histogram-fields         actor-histogram-fields
    ;; :enumerable-fields        actor-enumerable-fields
    }))

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
   :routes                asset-routes})
