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
                             SourcableEntityFilterParams]
              :as routes.common]
             [crud :refer [capitalize-entity
                           revoke-request
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

(s/defn judgement-revocation-routes
  "Like ctia.http.routes.crud/revocation-routes except defines a
  custom :revocation-update-fn and has an extra :reason query-param.
  It's unclear how to reliably specify and retrieve :reason dynamically,
  which is why this is a separate function."
  [services :- APIHandlerServices
   {:keys [entity
           entity-schema
           post-capabilities] :as entity-crud-config}]
  (let [capabilities post-capabilities]
    (POST "/:id/expire" req
          :summary (format "Expires the supplied %s" (capitalize-entity entity))
          :path-params [id :- s/Str]
          :query-params [reason :- (describe s/Str "Message to append to the Judgement's reason value")
                         {wait_for :- (describe s/Bool "wait for entity to be available for search") nil}]
          :return entity-schema
          :description (routes.common/capabilities->description capabilities)
          :capabilities capabilities
          :auth-identity identity
          :identity-map identity-map
          (revoke-request req services
                          entity-crud-config
                          {:id id
                           :identity identity
                           :identity-map identity-map
                           :revocation-update-fn (fn [entity _]
                                                   (-> entity
                                                       (update :reason str " " reason)))
                           :wait_for wait_for}))))

(s/defn judgement-routes [services :- APIHandlerServices]
  (let [entity-crud-config {:entity :judgement
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
                            :enumerable-fields js/judgement-enumerable-fields}]
    (routes
      (services->entity-crud-routes
        services
        entity-crud-config)
      (judgement-revocation-routes
        services
        entity-crud-config))))

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
   :services->routes (routes.common/reloadable-function
                       judgement-routes)
   :capabilities capabilities})
