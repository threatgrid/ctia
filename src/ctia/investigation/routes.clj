(ns ctia.investigation.routes
  (:require [ctia.domain.entities :refer [realize-investigation]]
            [ctia.http.routes.crud :refer [entity-crud-routes]]
            [ctia.http.routes.common
             :refer
             [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
            [ctia.schemas
             [core :refer [Investigation NewInvestigation PartialInvestigation PartialInvestigationList]]
             [sorting :as sorting]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def investigation-sort-fields
  (apply s/enum sorting/investigation-sort-fields))

(def investigation-select-fields
  (apply s/enum (concat sorting/investigation-sort-fields
                        [:description
                         :type
                         :search-txt
                         :short_description
                         :created_at])))

(s/defschema InvestigationFieldsParam
  {(s/optional-key :fields) [investigation-select-fields]})

(s/defschema InvestigationSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   InvestigationFieldsParam
   {:query s/Str}
   {s/Keyword s/Any}))

(def InvestigationGetParams InvestigationFieldsParam)

(s/defschema InvestigationsByExternalIdQueryParams
  (st/merge
   InvestigationFieldsParam
   PagingParams))

(def investigation-routes
  (entity-crud-routes
   {:entity :investigation
    :new-schema NewInvestigation
    :entity-schema Investigation
    :get-schema PartialInvestigation
    :get-params InvestigationGetParams
    :list-schema PartialInvestigationList
    :search-schema PartialInvestigationList
    :external-id-q-params InvestigationsByExternalIdQueryParams
    :search-q-params InvestigationSearchParams
    :new-spec :new-investigation/map
    :realize-fn realize-investigation
    :get-capabilities :read-investigation
    :post-capabilities :create-investigation
    :put-capabilities :create-investigation
    :delete-capabilities :delete-investigation
    :search-capabilities :search-investigation
    :external-id-capabilities #{:read-investigation :external-id}}))
