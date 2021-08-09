(ns ctia.entity.asset-mapping
  (:require
   [ctia.domain.entities :as entities]
   [ctia.entity.asset :as asset]
   [ctia.flows.schemas :refer [with-error]]
   [ctia.graphql.delayed :as delayed]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.schemas.core :as schemas :refer
    [RealizeFnResult GraphQLRuntimeContext APIHandlerServices def-acl-schema]]
   [ctia.schemas.sorting :as sorting]
   [ctia.store]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
   [ctim.schemas.asset-mapping :as asset-mapping-schema]
   [flanders.utils :as fu]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def-acl-schema AssetMapping
  asset-mapping-schema/AssetMapping
  "asset-mapping")

(def-acl-schema PartialAssetMapping
  (fu/optionalize-all asset-mapping-schema/AssetMapping)
  "partial-asset-mapping")

(s/defschema PartialAssetMappingList
  [PartialAssetMapping])

(def-acl-schema NewAssetMapping
  asset-mapping-schema/NewAssetMapping
  "new-asset-mapping")

(schemas/def-stored-schema StoredAssetMapping AssetMapping)

(s/defschema PartialNewAssetMapping
  (st/optional-keys-schema NewAssetMapping))

(s/defschema PartialStoredAssetMapping
  (st/optional-keys-schema StoredAssetMapping))

(def asset-mapping-default-realize
  (entities/default-realize-fn "asset-mapping" NewAssetMapping StoredAssetMapping))

(s/defn realize-asset-mapping
  :- (RealizeFnResult (with-error StoredAssetMapping))
  [{:keys [asset_ref]
    :as   new-entity}
   id tempids & rest-args]
  (delayed/fn :- (with-error StoredAssetMapping)
              [rt-ctx :- GraphQLRuntimeContext]
              (-> asset-mapping-default-realize
                  (schemas/lift-realize-fn-with-context rt-ctx)
                  (apply new-entity id tempids rest-args)
                  (asset/set-asset-ref tempids))))

(def asset-mapping-mapping
  {"asset-mapping"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:valid_time  em/valid-time
      :confidence  em/token
      :specificity em/token
      :stability   em/token
      :observable  em/observable
      :asset_ref   em/token})}})

(def-es-store AssetMappingStore :asset-mapping StoredAssetMapping PartialStoredAssetMapping)

(def asset-mapping-fields
  (concat sorting/base-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:confidence
           :specificity
           :stability
           :observable.value
           :observable.type
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
  [:source
   :confidence
   :specificity
   :stability])

(def AssetMappingGetParams AssetMappingFieldsParam)

(s/defschema AssetMappingSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   AssetMappingFieldsParam
   (st/optional-keys
    {:sort_by   asset-mapping-sort-fields
     :asset_ref s/Str})))

(def asset-mapping-histogram-fields
  [:timestamp
   :valid_time.start_time
   :valid_time.end_time])

(def asset-mapping-can-revoke? true)

(s/defn asset-mapping-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :asset-mapping
    :new-schema               NewAssetMapping
    :entity-schema            AssetMapping
    :get-schema               PartialAssetMapping
    :get-params               AssetMappingGetParams
    :list-schema              PartialAssetMappingList
    :search-schema            PartialAssetMappingList
    :external-id-q-params     AssetMappingByExternalIdQueryParams
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
    :can-revoke?              asset-mapping-can-revoke?
    :searchable-fields        (routes.common/searchable-fields
                               {:schema AssetMapping})}))

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
   :services->routes      (routes.common/reloadable-function
                            asset-mapping-routes)
   :capabilities          capabilities
   :fields                asset-mapping-fields
   :sort-fields           asset-mapping-fields})
