(ns ctia.sighting.routes
  (:require [ctia.domain.entities :refer [realize-sighting]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [NewSighting PartialSighting PartialSightingList Sighting]]
             [sorting :as sorting]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def sighting-sort-fields
  (apply s/enum sorting/sighting-sort-fields))

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
    :new-schema NewSighting
    :entity-schema Sighting
    :get-schema PartialSighting
    :get-params SightingGetParams
    :list-schema PartialSightingList
    :search-schema PartialSightingList
    :external-id-q-params SightingByExternalIdQueryParams
    :search-q-params SightingSearchParams
    :new-spec :new-sighting/map
    :realize-fn realize-sighting
    :get-capabilities :read-sighting
    :post-capabilities :create-sighting
    :put-capabilities :create-sighting
    :delete-capabilities :delete-sighting
    :search-capabilities :search-sighting
    :external-id-capabilities #{:read-sighting :external-id}}))
