(ns ctia.http.routes.feedback
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :refer [realize-feedback]]
   [ctia.domain.entities.feedback :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :refer [created paginated-ok PagingParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewFeedback StoredFeedback]]
   [ring.util.http-response :refer [ok no-content not-found]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema FeedbackQueryParams
  (st/merge
   PagingParams
   {:entity_id s/Str
    (s/optional-key :sort_by) (s/enum :id :feedback :reason)}))

(s/defschema FeedbackByExternalIdQueryParams
  (st/merge (st/dissoc FeedbackQueryParams :entity_id)
            {:external_id s/Str}))

(defroutes feedback-routes
  (context "/feedback" []
    :tags ["Feedback"]
    (POST "/" []
      :return StoredFeedback
      :body [feedback NewFeedback {:description "a new Feedback on an entity"}]
      :summary "Adds a new Feedback"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-feedback
      :identity identity
      (created
       (with-long-id
         (first
          (flows/create-flow :realize-fn realize-feedback
                             :store-fn #(write-store :feedback create-feedbacks %)
                             :entity-type :feedback
                             :identity identity
                             :entities [feedback])))))

    (GET "/" []
      :return [StoredFeedback]
      :query [params FeedbackQueryParams]
      :summary "Search Feedback"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-feedback
      (paginated-ok
       (page-with-long-id
        (read-store :feedback
                    list-feedback
                    (select-keys params [:entity_id])
                    (dissoc params :entity_id)))))

    (GET "/external_id" []
      :return [(s/maybe StoredFeedback)]
      :query [q FeedbackByExternalIdQueryParams]

      :header-params [api_key :- (s/maybe s/Str)]
      :summary "List feedback by external id"
      :capabilities #{:read-feedback :external-id}
      (paginated-ok
       (page-with-long-id
        (read-store :feedback list-feedback
                    {:external_ids (:external_id q)} q))))

    (GET "/:id" []
      :return (s/maybe StoredFeedback)
      :summary "Gets a Feedback by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-feedback
      (if-let [d (read-store :feedback read-feedback id)]
        (ok (with-long-id d))
        (not-found)))

    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a feedback"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-feedback
      :identity identity
      (if (flows/delete-flow :get-fn #(read-store :feedback read-feedback %)
                             :delete-fn #(write-store :feedback delete-feedback %)
                             :entity-type :feedback
                             :entity-id id
                             :identity identity)
        (no-content)
        (not-found)))))
