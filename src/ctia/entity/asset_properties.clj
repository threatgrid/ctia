(ns ctia.entity.asset-properties
  (:require [compojure.api.sweet :refer [POST routes]]
            [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.domain.entities :as entities]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common :as routes.common]
            [ctia.http.routes.crud :refer [services->entity-crud-routes]]
            [ctia.http.routes.crud :refer [entity-crud-routes]]
            [ctia.schemas.core :refer [def-acl-schema def-stored-schema APIHandlerServices]]
            [ctia.schemas.core :refer [def-acl-schema def-stored-schema]]
            [ctia.schemas.sorting :as sorting]
            [ctia.schemas.utils :as csu]
            [ctia.store]
            [ctia.stores.es.mapping :as em]
            [ctia.stores.es.store :refer [def-es-store]]
            [ctim.schemas.asset-properties :as asset-properties-schema]
            [flanders.utils :as fu]
            [ring.util.http-response :as http-response]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def-acl-schema AssetProperties
  asset-properties-schema/AssetProperties
  "asset-properties")

(def-acl-schema PartialAssetProperties
  (fu/optionalize-all asset-properties-schema/AssetProperties)
  "partial-asset-properties")

(s/defschema PartialAssetPropertiesList
  [PartialAssetProperties])

(def-acl-schema NewAssetProperties
  asset-properties-schema/NewAssetProperties
  "new-asset-properties")

(def-stored-schema StoredAssetProperties AssetProperties)

(s/defschema PartialStoredAssetProperties
  (csu/optional-keys-schema StoredAssetProperties))

(def realize-asset-properties
  (default-realize-fn "asset-properties" NewAssetProperties StoredAssetProperties))

(def ^:private properties-mapping
  {:type "object"
   :properties
   {:name  em/token
    :value em/token}})

(def asset-properties-mapping
  {"asset-properties"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:valid_time  em/valid-time
      :asset_ref   em/token
      :properties properties-mapping})}})

(def-es-store AssetPropertiesStore :asset-properties StoredAssetProperties PartialStoredAssetProperties)

(def asset-properties-fields
  (concat sorting/base-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:asset_ref
           :properties.name
           :properties.value]))

(def asset-properties-sort-fields
  (apply s/enum asset-properties-fields))

(s/defschema AssetPropertiesFieldsParam
  {(s/optional-key :fields) [asset-properties-sort-fields]})

(s/defschema AssetPropertiesByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   AssetPropertiesFieldsParam))

(def asset-properties-enumerable-fields
  [:source
   :properties.name
   :properties.value])

(def AssetPropertiesGetParams AssetPropertiesFieldsParam)

(s/defschema AssetPropertiesSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   AssetPropertiesFieldsParam
   (st/optional-keys
    {:query     s/Str
     :sort_by   asset-properties-sort-fields
     :asset_ref s/Str})))

(def asset-properties-histogram-fields
  [:timestamp
   :valid_time.start_time
   :valid_time.end_time])

(s/defn additional-routes [{{:keys [now]} :CTIATimeService
                            {:keys [read-store write-store]} :StoreService
                            :as                              services} :- APIHandlerServices]
  (routes
   (POST "/expire/:id" []
     :return         AssetProperties
     :path-params    [id :- s/Str]
     :summary        "Expire 'valid-time' field of AssetProperties entity"
     :capabilities   :create-asset-properties
     :auth-identity  identity
     :identity-map   identity-map
     (if-let [updated
              (flows/patch-flow
               :services services
               :get-fn (fn [_] (read-store :asset-properties
                                           ctia.store/read-record
                                           id
                                           identity-map
                                           {}))
               :realize-fn realize-asset-properties
               :update-fn #(write-store :asset-properties
                                        ctia.store/update-record
                                        (:id %)
                                        (assoc-in % [:valid_time :end_time] (now))
                                        identity-map
                                        {})
               :long-id-fn #(entities/with-long-id % services)
               :entity-type :asset-properties
               :entity-id id
               :identity identity
               :patch-operation :replace
               :partial-entity {}
               :spec :new-asset-properties/map)]
       (http-response/ok (entities/un-store updated))
       (http-response/not-found {:error "asset-properties not found"})))))

(s/defn asset-properties-routes [services :- APIHandlerServices]
  (routes
   (additional-routes services)
   (services->entity-crud-routes
    services
    {:entity                   :asset-properties
     :new-schema               NewAssetProperties
     :entity-schema            AssetProperties
     :get-schema               PartialAssetProperties
     :get-params               AssetPropertiesGetParams
     :list-schema              PartialAssetPropertiesList
     :search-schema            PartialAssetPropertiesList
     :external-id-q-params     AssetPropertiesByExternalIdQueryParams
     :search-q-params          AssetPropertiesSearchParams
     :new-spec                 :new-asset-properties/map
     :realize-fn               realize-asset-properties
     :get-capabilities         :read-asset-properties
     :post-capabilities        :create-asset-properties
     :put-capabilities         :create-asset-properties
     :delete-capabilities      :delete-asset-properties
     :search-capabilities      :search-asset-properties
     :external-id-capabilities :read-asset-properties
     :can-aggregate?           true
     :histogram-fields         asset-properties-histogram-fields
     :enumerable-fields        asset-properties-enumerable-fields})))

(def capabilities
  #{:create-asset-properties
    :read-asset-properties
    :delete-asset-properties
    :search-asset-properties})

(def asset-properties-entity
  {:route-context         "/asset-properties"
   :tags                  ["Asset Properties"]
   :entity                :asset-properties
   :plural                :asset-properties
   :new-spec              :new-asset-properties/map
   :schema                AssetProperties
   :partial-schema        PartialAssetProperties
   :partial-list-schema   PartialAssetPropertiesList
   :new-schema            NewAssetProperties
   :stored-schema         StoredAssetProperties
   :partial-stored-schema PartialStoredAssetProperties
   :realize-fn            realize-asset-properties
   :es-store              ->AssetPropertiesStore
   :es-mapping            asset-properties-mapping
   :services->routes  asset-properties-routes
   :capabilities          capabilities})
