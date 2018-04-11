(ns ctia.judgement.routes
  (:require [ctia.domain.entities :refer [realize-judgement]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [Judgement NewJudgement PartialJudgement PartialJudgementList]]
             [sorting :as sorting]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def judgements-by-observable-sort-fields
  (apply s/enum (map name (conj sorting/judgement-sort-fields
                                "disposition:asc,valid_time.start_time:desc"))))

(def judgement-sort-fields
  (apply s/enum sorting/judgement-sort-fields))

(s/defschema JudgementFieldsParam
  {(s/optional-key :fields) [judgement-sort-fields]})

(s/defschema JudgementsByObservableQueryParams
  (st/merge
   PagingParams
   JudgementFieldsParam
   {(s/optional-key :sort_by) judgements-by-observable-sort-fields}))

(s/defschema JudgementSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   JudgementFieldsParam
   {:query s/Str
    (s/optional-key :disposition_name) s/Str
    (s/optional-key :disposition) s/Int
    (s/optional-key :priority) s/Int
    (s/optional-key :severity) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :sort_by)  judgement-sort-fields}))

(def JudgementGetParams JudgementFieldsParam)

(s/defschema JudgementsQueryParams
  (st/merge
   PagingParams
   JudgementFieldsParam
   {(s/optional-key :sort_by) judgement-sort-fields}))

(s/defschema JudgementsByExternalIdQueryParams
  (st/merge
   JudgementsQueryParams
   JudgementFieldsParam))

(def judgement-routes
  (entity-crud-routes
   {:entity :judgement
    :new-schema NewJudgement
    :entity-schema Judgement
    :get-schema PartialJudgement
    :get-params JudgementGetParams
    :list-schema PartialJudgementList
    :search-schema PartialJudgementList
    :external-id-q-params JudgementsByExternalIdQueryParams
    :search-q-params JudgementSearchParams
    :new-spec :new-judgement/map
    :realize-fn realize-judgement
    :get-capabilities :read-judgement
    :post-capabilities :create-judgement
    :delete-capabilities :delete-judgement
    :search-capabilities :search-judgement
    :external-id-capabilities #{:read-judgement :external-id}
    :can-update? false}))
