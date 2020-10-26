(ns ctia.entity.asset-mapping
  (:require [clj-momo.lib.clj-time.core :as time]
            [compojure.api.core :refer [context routes]]
            [compojure.api.resource :refer [resource]]
            [ctia.domain.entities :as entities]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common :as routes.common]
            [ctia.http.routes.crud :refer [services->entity-crud-routes]]
            [ctia.schemas.core :refer [def-acl-schema def-stored-schema APIHandlerServices DelayedRoutesOptions]]
            [ctia.schemas.sorting :as sorting]
            [ctia.schemas.utils :as csu]
            [ctia.store]
            [ctia.stores.es.mapping :as em]
            [ctia.stores.es.store :refer [def-es-store]]
            [ctim.schemas.asset-mapping :as asset-mapping-schema]
            [flanders.utils :as fu]
            [ring.util.http-response :as http-response]
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

(def-stored-schema StoredAssetMapping AssetMapping)

(s/defschema PartialNewAssetMapping
  (csu/optional-keys-schema NewAssetMapping))

(s/defschema PartialStoredAssetMapping
  (csu/optional-keys-schema StoredAssetMapping))

(def realize-asset-mapping
  (entities/default-realize-fn "asset-mapping" NewAssetMapping StoredAssetMapping))

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
   AssetMappingFieldsParam
   (st/optional-keys
    {:query     s/Str
     :sort_by   asset-mapping-sort-fields
     :asset_ref s/Str})))

(def asset-mapping-histogram-fields
  [:timestamp
   :valid_time.start_time
   :valid_time.end_time])

(s/defn additional-routes [{{{:keys [get-in-config]} :ConfigService
                             {:keys [read-store write-store]} :StoreService
                             :as services}
                            :services
                            :keys [tags]} :- DelayedRoutesOptions]
  (routes
   (context "/expire/:id" []
     :return         AssetMapping
     :path-params    [id :- s/Str]
     :summary        "Expire Asset Mapping's valid-time property"
     :capabilities   :create-asset-mapping
     :auth-identity  identity
     :identity-map   identity-map
     (resource
      {:tags tags
       :post
       {:handler
        (fn [_]
          (if-let [updated
                   (flows/patch-flow
                    :services services
                    :get-fn (fn [_] (read-store :asset-mapping
                                                ctia.store/read-record
                                                id
                                                identity-map
                                                {}))
                    :realize-fn realize-asset-mapping
                    :update-fn #(write-store :asset-mapping
                                             ctia.store/update-record
                                             (:id %)
                                             (assoc-in % [:valid_time :end_time] (time/internal-now))
                                             identity-map
                                             {})
                    :long-id-fn #(entities/with-long-id % get-in-config)
                    :entity-type :asset-mapping
                    :entity-id id
                    :identity identity
                    :patch-operation :replace
                    :partial-entity {}
                    :spec :new-asset-mapping/map)]
            (http-response/ok (entities/un-store updated))
            (http-response/not-found {:error "asset-mapping not found"})))}}))))

(s/defn asset-mapping-routes [delayed-routes-opts :- DelayedRoutesOptions]
  (routes
   (additional-routes delayed-routes-opts)
   (services->entity-crud-routes
    delayed-routes-opts
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
     :enumerable-fields        asset-mapping-enumerable-fields})))

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
   :services->routes      asset-mapping-routes
   :capabilities          capabilities})
