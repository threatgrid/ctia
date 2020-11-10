(ns ctia.entity.judgement
  (:require [clj-momo.lib.clj-time.core :as time]
            [compojure.api.core :refer [context POST routes]]
            [compojure.api.resource :refer [resource]]
            [ctia.domain.entities :refer [un-store with-long-id]]
            [ctia.entity.feedback.graphql-schemas :as feedback]
            [ctia.entity.judgement
             [es-store :as j-store]
             [schemas :as js]]
            [ctia.entity.relationship.graphql-schemas :as relationship]
            [ctia.flows.crud :as flows]
            ;; do not delete, defmethod side effects
            [ctia.http.middleware.auth]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams
                             PagingParams
                             SourcableEntityFilterParams]]
             [crud :refer [fill-delayed-routes-config-defaults 
                           revocation-routes*
                           services->entity-crud-routes]]]
            [ctia.schemas.core :refer [APIHandlerServices Entity]]
            [ctia.schemas.graphql
             [flanders :as f]
             [helpers :as g]
             [pagination :as pagination]
             [refs :as refs]
             [sorting :as graphql-sorting]]
            [ctim.schemas.judgement :as judgement]
            [flanders.utils :as fu]
            [ring.swagger.schema :refer [describe]]
            [ring.util.http-response :refer [not-found ok]]
            [schema-tools.core :as st]
            [schema.core :as s]
            [ctia.schemas.graphql.ownership :as go]))

(def judgement-fields
  (apply s/enum
         (map name js/judgement-fields)))

(def judgement-sort-fields
  (apply s/enum
         (map name js/judgement-sort-fields)))

(def judgements-by-observable-sort-fields
  (apply s/enum
         (map name
              (concat js/judgements-by-observable-sort-fields
                      js/judgement-sort-fields))))

(s/defschema JudgementFieldsParam
  {(s/optional-key :fields) [judgement-fields]})

(s/defschema JudgementsByObservableQueryParams
  (st/merge
   PagingParams
   JudgementFieldsParam
   {(s/optional-key :sort_by)
    judgements-by-observable-sort-fields}))

(s/defschema JudgementSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   JudgementFieldsParam
   (st/optional-keys
   {:query s/Str
    :disposition_name s/Str
    :disposition s/Int
    :priority s/Int
    :severity s/Str
    :confidence s/Str
    :sort_by judgement-sort-fields})))

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

;; must be a macro to pass hygienic forms to :extra-query-params-syntax.
;; otherwise, eg., `describe` could be captured by some internal binding of revocation-routes*.
(defmacro judgement-revocation-routes
  [services ;:- APIHandlerServices
   delayed-routes-config]
  `(revocation-routes*
     ~services
     (fill-delayed-routes-config-defaults
       ~delayed-routes-config)
     :extra-query-params-syntax [~'reason :- (describe s/Str "Message to append to the Judgement's reason value")]))

(s/defn judgement-routes [services :- APIHandlerServices]
  (let [delayed-routes-config {:entity :judgement
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
                               :put-capabilities #{:create-judgement :developer}
                               :delete-capabilities :delete-judgement
                               :search-capabilities :search-judgement
                               :external-id-capabilities :read-judgement
                               :can-update? true
                               :can-aggregate? true
                               :histogram-fields js/judgement-histogram-fields
                               :revocation-update-fn (fn [entity {{{:keys [reason]} :query-params} :req}]
                                                       (-> entity
                                                           (update :reason str " " reason)))
                               :enumerable-fields js/judgement-enumerable-fields}]
    (routes
      (services->entity-crud-routes
        services
        delayed-routes-config)
      (judgement-revocation-routes
        services
        delayed-routes-config))))

(def capabilities
  #{:create-judgement
    :read-judgement
    :list-judgements
    :delete-judgement
    :search-judgement
    :developer})

(def JudgementType
  (let [{:keys [fields name description]}
        (f/->graphql (fu/optionalize-all judgement/Judgement)
                     {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object
     name
     description
     []
     (merge
      fields
      feedback/feedback-connection-field
      relationship/relatable-entity-fields
      go/graphql-ownership-fields))))

(def judgement-order-arg
  (graphql-sorting/order-by-arg
   "JudgementOrder"
   "judgements"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              js/judgement-fields))))

(def JudgementConnectionType
  (pagination/new-connection JudgementType))

(s/def judgement-entity :- Entity
  {:route-context "/judgement"
   :tags ["Judgement"]
   :entity :judgement
   :plural :judgements
   :new-spec :new-judgement/map
   :schema js/Judgement
   :partial-schema js/PartialJudgement
   :partial-list-schema js/PartialJudgementList
   :new-schema js/NewJudgement
   :stored-schema js/StoredJudgement
   :partial-stored-schema js/PartialStoredJudgement
   :realize-fn js/realize-judgement
   :es-store j-store/->JudgementStore
   :es-mapping j-store/judgement-mapping-def
   :services->routes judgement-routes
   :capabilities capabilities})
