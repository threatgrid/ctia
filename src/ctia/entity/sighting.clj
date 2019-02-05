(ns ctia.entity.sighting
  (:require [ctia.entity.sighting
             [es-store :as s-store]
             [schemas :as ss]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def sighting-sort-fields
  (apply s/enum ss/sighting-fields))

(s/defschema SightingFieldsParam
  {(s/optional-key :fields) [sighting-sort-fields]})

(s/defschema SightingSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   SightingFieldsParam
   {:query s/Str
    (s/optional-key :sensor) s/Str
    (s/optional-key :observables.value) s/Str
    (s/optional-key :observables.type) s/Str
    (s/optional-key :sort_by)  sighting-sort-fields}))

(s/defschema SightingsByObservableQueryParams
  (st/merge
   PagingParams
   SightingFieldsParam
   {(s/optional-key :sort_by)
    (s/enum
     :id
     :timestamp
     :confidence
     :observed_time.start_time)}))

(def SightingGetParams SightingFieldsParam)

(s/defschema SightingByExternalIdQueryParams
  (st/merge
   PagingParams
   SightingFieldsParam))

(def sighting-routes
  (entity-crud-routes
   {:entity :sighting
    :new-schema ss/NewSighting
    :entity-schema ss/Sighting
    :get-schema ss/PartialSighting
    :get-params SightingGetParams
    :list-schema ss/PartialSightingList
    :search-schema ss/PartialSightingList
    :external-id-q-params SightingByExternalIdQueryParams
    :search-q-params SightingSearchParams
    :new-spec :new-sighting/map
    :realize-fn ss/realize-sighting
    :get-capabilities :read-sighting
    :post-capabilities :create-sighting
    :put-capabilities :create-sighting
    :delete-capabilities :delete-sighting
    :search-capabilities :search-sighting
    :external-id-capabilities #{:read-sighting :external-id}}))

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
   :routes sighting-routes
   :capabilities capabilities})
