(ns ctia.indicator.routes
  (:require [ctia.domain.entities :refer [realize-indicator]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [Indicator NewIndicator PartialIndicator PartialIndicatorList]]
             [sorting :as sorting]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def indicator-sort-fields
  (apply s/enum sorting/indicator-sort-fields))

(s/defschema IndicatorFieldsParam
  {(s/optional-key :fields) [indicator-sort-fields]})

(s/defschema IndicatorSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   IndicatorFieldsParam
   {:query s/Str
    (s/optional-key :indicator_type) s/Str
    (s/optional-key :tags) s/Int
    (s/optional-key :kill_chain_phases) s/Str
    (s/optional-key :producer) s/Str
    (s/optional-key :specification) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :sort_by)  indicator-sort-fields}))

(def IndicatorGetParams IndicatorFieldsParam)

(s/defschema IndicatorsListQueryParams
  (st/merge
   PagingParams
   IndicatorFieldsParam
   {(s/optional-key :sort_by) indicator-sort-fields}))

(s/defschema IndicatorsByExternalIdQueryParams
  IndicatorsListQueryParams)

(def indicator-routes
  (entity-crud-routes
   {:entity :indicator
    :new-schema NewIndicator
    :entity-schema Indicator
    :get-schema PartialIndicator
    :get-params IndicatorGetParams
    :list-schema PartialIndicatorList
    :search-schema PartialIndicatorList
    :external-id-q-params IndicatorsByExternalIdQueryParams
    :search-q-params IndicatorSearchParams
    :new-spec :new-indicator/map
    :realize-fn realize-indicator
    :get-capabilities :read-indicator
    :post-capabilities :create-indicator
    :put-capabilities :create-indicator
    :delete-capabilities :delete-indicator
    :search-capabilities :search-indicator
    :external-id-capabilities #{:read-indicator :external-id}}))
