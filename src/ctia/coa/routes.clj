(ns ctia.coa.routes
  (:require [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.domain.entities :refer [realize-coa]]
            [ctia.schemas
             [core :refer [COA NewCOA PartialCOA PartialCOAList]]
             [sorting :as sorting]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def coa-sort-fields
  (apply s/enum sorting/coa-sort-fields))

(s/defschema COAFieldsParam
  {(s/optional-key :fields) [coa-sort-fields]})

(s/defschema COASearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   COAFieldsParam
   {:query s/Str
    (s/optional-key :stage) s/Str
    (s/optional-key :coa_type) s/Str
    (s/optional-key :impact) s/Str
    (s/optional-key :objective) s/Str
    (s/optional-key :cost) s/Str
    (s/optional-key :efficacy) s/Str
    (s/optional-key :structured_coa_type) s/Str
    (s/optional-key :sort_by) coa-sort-fields}))

(def COAGetParams COAFieldsParam)

(s/defschema COAByExternalIdQueryParams
  (st/merge
   PagingParams
   COAFieldsParam))

(def coa-routes
  (entity-crud-routes
   {:entity :coa
    :new-schema NewCOA
    :entity-schema COA
    :get-schema PartialCOA
    :get-params COAGetParams
    :list-schema PartialCOAList
    :search-schema PartialCOAList
    :external-id-q-params COAByExternalIdQueryParams
    :search-q-params COASearchParams
    :new-spec :new-coa/map
    :realize-fn realize-coa
    :get-capabilities :read-coa
    :post-capabilities :create-coa
    :put-capabilities :create-coa
    :delete-capabilities :delete-coa
    :search-capabilities :search-coa
    :external-id-capabilities #{:read-coa :external-id}}))
