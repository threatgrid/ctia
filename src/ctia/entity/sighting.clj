(ns ctia.entity.sighting
  (:require
   [ctia.entity.sighting.es-store :as s-store]
   [ctia.entity.sighting.schemas :as ss]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.schemas.core :refer [APIHandlerServices]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def sighting-sort-fields
  (apply s/enum (map name ss/sighting-sort-fields)))

(def sighting-fields
  (apply s/enum (map name ss/sighting-fields)))

(s/defschema SightingFieldsParam
  {(s/optional-key :fields) [sighting-fields]})

(s/defschema SightingSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   SightingFieldsParam
   (st/optional-keys
    {:sensor            s/Str
     :observables.value s/Str
     :observables.type  s/Str
     :sort_by           sighting-sort-fields})))

(s/defschema SightingsByObservableQueryParams
  (st/merge
   routes.common/PagingParams
   SightingFieldsParam
   {(s/optional-key :sort_by) sighting-sort-fields}))

(def SightingGetParams SightingFieldsParam)

(s/defschema SightingByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   SightingFieldsParam))

(s/defn sighting-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :sighting
    :new-schema               ss/NewSighting
    :entity-schema            ss/Sighting
    :get-schema               ss/PartialSighting
    :get-params               SightingGetParams
    :list-schema              ss/PartialSightingList
    :search-schema            ss/PartialSightingList
    :external-id-q-params     SightingByExternalIdQueryParams
    :external-id-capabilities :read-sighting
    :search-q-params          SightingSearchParams
    :new-spec                 :new-sighting/map
    :realize-fn               ss/realize-sighting
    :get-capabilities         :read-sighting
    :post-capabilities        :create-sighting
    :put-capabilities         :create-sighting
    :delete-capabilities      :delete-sighting
    :search-capabilities      :search-sighting
    :can-aggregate?           true
    :histogram-fields         ss/sighting-histogram-fields
    :enumerable-fields        ss/sighting-enumerable-fields
    :searchable-fields        (routes.common/searchable-fields
                               {:fields ss/sighting-fields
                                :ignore [:observed_time.start_time
                                         :observed_time.end_time
                                         :count]})}))

(def capabilities
  #{:create-sighting
    :read-sighting
    :list-sightings
    :delete-sighting
    :search-sighting})

(def sighting-entity
  {:route-context         "/sighting"
   :tags                  ["Sighting"]
   :entity                :sighting
   :plural                :sightings
   :new-spec              :new-sighting/map
   :schema                ss/Sighting
   :partial-schema        ss/PartialSighting
   :partial-list-schema   ss/PartialSightingList
   :new-schema            ss/NewSighting
   :stored-schema         ss/StoredSighting
   :partial-stored-schema ss/PartialStoredSighting
   :realize-fn            ss/realize-sighting
   :es-store              s-store/->SightingStore
   :es-mapping            s-store/sighting-mapping
   :services->routes      (routes.common/reloadable-function sighting-routes)
   :capabilities          capabilities
   :fields                ss/sighting-fields
   :sort-fields           ss/sighting-sort-fields})
