(ns ctia.entity.feedback
  (:require [compojure.api.core :refer [GET routes]]
            [ctia.domain.entities :refer [page-with-long-id un-store-page]]
            [ctia.entity.feedback.schemas :as fs]
            [ctia.http.routes
             [common :refer [paginated-ok PagingParams]
              :as routes.common]
             [crud :refer [services->entity-crud-routes]]]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [ctia.schemas.sorting :as sorting]
            [ctia.store :refer [list-records]]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def feedback-mapping
  {"feedback"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:entity_id em/token
      :feedback em/integer-type
      :reason em/sortable-text})}})

(def-es-store FeedbackStore :feedback fs/StoredFeedback fs/PartialStoredFeedback)

(def feedback-sort-fields
  (apply s/enum fs/feedback-fields))

(s/defschema FeedbackFieldsParam
  {(s/optional-key :fields) [feedback-sort-fields]})

(s/defschema FeedbackQueryParams
  (st/merge
   FeedbackFieldsParam
   PagingParams
   {:entity_id s/Str
    (s/optional-key :sort_by) feedback-sort-fields}))

(def FeedbackGetParams FeedbackFieldsParam)

(s/defschema FeedbackByExternalIdQueryParams
  (st/dissoc FeedbackQueryParams :entity_id))

(comment
  (s/defschema FeedbacksByJudgementQueryParams
    (st/merge
     PagingParams
     JudgementFieldsParam
     {(s/optional-key :sort_by) feedback-sort-fields})))

(s/defn feedback-by-entity-route [{{:keys [read-store]} :StoreService
                                   :as services} :- APIHandlerServices]
  (let [capabilites :read-feedback]
    (GET "/" []
         :return fs/PartialFeedbackList
         :query [params FeedbackQueryParams]
         :summary "Search Feedback"
         :description (routes.common/capabilities->description capabilites)
         :capabilities :read-feedback
         :auth-identity identity
         :identity-map identity-map
         (-> (read-store :feedback
                         list-records
                         {:all-of (select-keys params [:entity_id])}
                         identity-map
                         (dissoc params :entity_id))
             (page-with-long-id services)
             un-store-page
             paginated-ok))))

(def capabilities
  #{:create-feedback
    :read-feedback
    :delete-feedback})

(s/defn feedback-routes [services :- APIHandlerServices]
  (routes
   (feedback-by-entity-route services)
   (services->entity-crud-routes
    services
    {:entity :feedback
     :new-schema fs/NewFeedback
     :entity-schema fs/Feedback
     :get-schema fs/PartialFeedback
     :get-params FeedbackGetParams
     :list-schema fs/PartialFeedbackList
     :external-id-q-params FeedbackByExternalIdQueryParams
     :realize-fn fs/realize-feedback
     :get-capabilities :read-feedback
     :post-capabilities :create-feedback
     :put-capabilities :create-feedback
     :delete-capabilities :delete-feedback
     :external-id-capabilities :read-feedback
     :spec :new-feedback/map
     :can-search? false
     :enumerable-fields []
     :can-update? false})))

(def feedback-entity
  {:route-context "/feedback"
   :tags ["Feedback"]
   :entity :feedback
   :plural :feedbacks
   :new-spec :new-feedback/map
   :schema fs/Feedback
   :partial-schema fs/PartialFeedback
   :partial-list-schema fs/PartialFeedbackList
   :new-schema fs/NewFeedback
   :stored-schema fs/StoredFeedback
   :partial-stored-schema fs/PartialStoredFeedback
   :realize-fn fs/realize-feedback
   :es-store ->FeedbackStore
   :es-mapping feedback-mapping
   :services->routes (routes.common/reloadable-function
                       feedback-routes)
   :capabilities capabilities})
