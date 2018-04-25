(ns ctia.entity.feedback
  (:require [compojure.api.sweet :refer [GET]]
            [ctia.domain.entities :refer [page-with-long-id un-store-page]]
            [ctia.store :refer :all]
            [ctia.entity.feedback.schemas :as fs]
            [ctia.http.routes
             [common :refer [paginated-ok PagingParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas.sorting :as sorting]
            [ctia.store :refer :all]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def feedback-mapping
  {"feedback"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:entity_id em/all_token
      :feedback {:type "integer"}
      :reason em/all_text})}})

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

(def feedback-by-entity-route
  (GET "/" []
       :return fs/PartialFeedbackList
       :query [params FeedbackQueryParams]
       :summary "Search Feedback"
       :header-params [{Authorization :- (s/maybe s/Str) nil}]
       :capabilities :read-feedback
       :auth-identity identity
       :identity-map identity-map
       (-> (read-store :feedback
                       list-records
                       (select-keys params [:entity_id])
                       identity-map
                       (dissoc params :entity_id))
           page-with-long-id
           un-store-page
           paginated-ok)))

(def capabilities
  #{:create-feedback
    :read-feedback
    :delete-feedback})

(def feedback-routes
  (entity-crud-routes
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
    :put-capabilities :create-feedbacl
    :delete-capabilities :delete-feedback
    :external-id-capabilities #{:read-feedback :external-id}
    :can-update? false}))

(def feedback-entity
  {:route-context "/feedback"
   :tags ["Feedback"]
   :entity :feedback
   :plural :feedbacks
   :schema fs/Feedback
   :partial-schema fs/PartialFeedback
   :partial-list-schema fs/PartialFeedbackList
   :new-schema fs/NewFeedback
   :stored-schema fs/StoredFeedback
   :partial-stored-schema fs/PartialStoredFeedback
   :realize-fn fs/realize-feedback
   :es-store ->FeedbackStore
   :es-mapping feedback-mapping
   :routes feedback-routes
   :capabilities capabilities})
