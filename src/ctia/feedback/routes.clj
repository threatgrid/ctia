(ns ctia.feedback.routes
  (:refer-clojure
   :exclude [identity
             list
             update
             read])
  (:require
   [compojure.api.sweet :refer [GET]]
   [ctia.store :refer :all]
   [ctia.domain.entities
    :refer [realize-feedback
            un-store-page
            page-with-long-id]]
   [ctia.http.routes
    [common :refer [PagingParams
                    paginated-ok]]
    [crud :refer [entity-crud-routes]]]
   [ctia.schemas
    [core :refer [Feedback NewFeedback PartialFeedback PartialFeedbackList]]
    [sorting :as sorting]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def feedback-sort-fields
  (apply s/enum sorting/feedback-sort-fields))

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
       :return PartialFeedbackList
       :query [params FeedbackQueryParams]
       :summary "Search Feedback"
       :header-params [{Authorization :- (s/maybe s/Str) nil}]
       :capabilities :read-feedback
       :identity identity
       :identity-map identity-map
       (-> (read-store :feedback
                       list
                       (select-keys params [:entity_id])
                       identity-map
                       (dissoc params :entity_id))
           page-with-long-id
           un-store-page
           paginated-ok)))

(def feedback-routes
  (entity-crud-routes
   {:entity :feedback
    :new-schema NewFeedback
    :entity-schema Feedback
    :get-schema PartialFeedback
    :get-params FeedbackGetParams
    :list-schema PartialFeedbackList
    :external-id-q-params FeedbackByExternalIdQueryParams
    :realize-fn realize-feedback
    :get-capabilities :read-feedback
    :post-capabilities :create-feedback
    :put-capabilities :create-feedbacl
    :delete-capabilities :delete-feedback
    :external-id-capabilities #{:read-feedback :external-id}
    :can-update? false}))
