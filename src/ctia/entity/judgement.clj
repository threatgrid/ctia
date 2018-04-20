(ns ctia.entity.judgement
  (:require [ctia.entity.feedback.graphql-schemas :as feedback]
            [ctia.entity.judgement
             [es-store :as j-store]
             [schemas :as js]]
            [ctia.entity.relationship.graphql-schemas :as relationship]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas.graphql
             [flanders :as f]
             [helpers :as g]
             [pagination :as pagination]
             [refs :as refs]
             [sorting :as graphql-sorting]]
            [ctim.schemas.judgement :as judgement]
            [flanders.utils :as fu]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def judgements-by-observable-sort-fields
  (apply s/enum (map name (conj js/judgement-fields
                                "disposition:asc,valid_time.start_time:desc"))))

(def judgement-sort-fields
  (apply s/enum js/judgement-fields))

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
    :new-schema js/NewJudgement
    :entity-schema js/Judgement
    :get-schema js/PartialJudgement
    :get-params JudgementGetParams
    :list-schema js/PartialJudgementList
    :search-schema js/PartialJudgementList
    :external-id-q-params JudgementsByExternalIdQueryParams
    :search-q-params JudgementSearchParams
    :new-spec :new-judgement/map
    :realize-fn js/realize-judgement
    :get-capabilities :read-judgement
    :post-capabilities :create-judgement
    :delete-capabilities :delete-judgement
    :search-capabilities :search-judgement
    :external-id-capabilities #{:read-judgement :external-id}
    :can-update? false}))

(def capabilities
  #{:create-judgement
    :read-judgement
    :list-judgements
    :delete-judgement
    :search-judgement})

(def JudgementType
  (let [{:keys [fields name description]}
        (f/->graphql (fu/optionalize-all judgement/Judgement)
                     {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship/relatable-entity-fields))))

(def judgement-order-arg
  (graphql-sorting/order-by-arg
   "JudgementOrder"
   "judgements"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              js/judgement-fields))))

(def JudgementConnectionType
  (pagination/new-connection JudgementType))

(def judgement-entity
  {:route-context "/judgement"
   :tags ["Judgement"]
   :entity :judgement
   :plural :judgements
   :schema js/Judgement
   :partial-schema js/PartialJudgement
   :partial-list-schema js/PartialJudgementList
   :new-schema js/NewJudgement
   :stored-schema js/StoredJudgement
   :partial-stored-schema js/PartialStoredJudgement
   :realize-fn js/realize-judgement
   :es-store j-store/->JudgementStore
   :es-mapping j-store/judgement-mapping-def
   :routes judgement-routes
   :capabilities capabilities})
