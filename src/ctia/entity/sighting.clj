(ns ctia.entity.sighting
  (:require [ctia.entity.sighting
             [es-store :as s-store]
             [schemas :as ss]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]
              :as routes.common]
             [crud :refer [services->entity-crud-routes]]]
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
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   SightingFieldsParam
   (st/optional-keys
    {:query s/Str
     :sensor s/Str
     :observables.value s/Str
     :observables.type s/Str
     :sort_by sighting-sort-fields})))

(s/defschema SightingsByObservableQueryParams
  (st/merge
   PagingParams
   SightingFieldsParam
   {(s/optional-key :sort_by) sighting-sort-fields}))

(def SightingGetParams SightingFieldsParam)

(s/defschema SightingByExternalIdQueryParams
  (st/merge
   PagingParams
   SightingFieldsParam))

(s/defn sighting-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity :sighting
    :new-schema ss/NewSighting
    :entity-schema ss/Sighting
    :get-schema ss/PartialSighting
    :get-params SightingGetParams
    :list-schema ss/PartialSightingList
    :search-schema ss/PartialSightingList
    :external-id-q-params SightingByExternalIdQueryParams
    :external-id-capabilities :read-sighting
    :search-q-params SightingSearchParams
    :new-spec :new-sighting/map
    :realize-fn ss/realize-sighting
    :get-capabilities :read-sighting
    :post-capabilities :create-sighting
    :put-capabilities :create-sighting
    :delete-capabilities :delete-sighting
    :search-capabilities :search-sighting
    :can-aggregate? true
    :histogram-fields ss/sighting-histogram-fields
    :enumerable-fields ss/sighting-enumerable-fields}))

(def capabilities
  #{:create-sighting
    :read-sighting
    :list-sightings
    :delete-sighting
    :search-sighting})

(def sighting-entity
  {:route-context "/sighting"
   :tags ["Sighting"]
   :entity :sighting
   :plural :sightings
   :new-spec :new-sighting/map
   :schema ss/Sighting
   :partial-schema ss/PartialSighting
   :partial-list-schema ss/PartialSightingList
   :new-schema ss/NewSighting
   :stored-schema ss/StoredSighting
   :partial-stored-schema ss/PartialStoredSighting
   :realize-fn ss/realize-sighting
   :es-store s-store/->SightingStore
   :es-mapping s-store/sighting-mapping
   :services->routes (routes.common/reloadable-function
                       sighting-routes)
   :capabilities capabilities})
