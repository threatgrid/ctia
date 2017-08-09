(ns ctia.http.routes.feedback
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.feedback :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :refer [created paginated-ok PagingParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewFeedback Feedback]]
   [ring.util.http-response :refer [ok no-content not-found]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema FeedbackQueryParams
  (st/merge
   PagingParams
   {:entity_id s/Str
    (s/optional-key :sort_by) (s/enum :id :feedback :reason)}))

(s/defschema FeedbackByExternalIdQueryParams
  (st/dissoc FeedbackQueryParams :entity_id))

(defroutes feedback-routes
  (context "/feedback" []
           :tags ["Feedback"]
           (POST "/" []
                 :return Feedback
                 :body [feedback NewFeedback {:description "a new Feedback on an entity"}]
                 :summary "Adds a new Feedback"
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :capabilities :create-feedback
                 :identity identity
                 (-> (flows/create-flow
                      :realize-fn ent/realize-feedback
                      :store-fn #(write-store :feedback create-feedbacks %)
                      :long-id-fn with-long-id
                      :entity-type :feedback
                      :identity identity
                      :entities [feedback])
                     first
                     ent/un-store
                     created))

           (GET "/" []
                :return [Feedback]
                :query [params FeedbackQueryParams]
                :summary "Search Feedback"
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-feedback
                (-> (read-store :feedback
                                list-feedback
                                (select-keys params [:entity_id])
                                (dissoc params :entity_id))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/external_id/:external_id" []
                :return [(s/maybe Feedback)]
                :query [q FeedbackByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List feedback by external id"
                :capabilities #{:read-feedback :external-id}
                (-> (read-store :feedback list-feedback
                                {:external_ids external_id} q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe Feedback)
                :summary "Gets a Feedback by ID"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-feedback
                (if-let [feedback (read-store :feedback read-feedback id)]
                  (-> feedback
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes a feedback"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-feedback
                   :identity identity
                   (if (flows/delete-flow
                        :get-fn #(read-store :feedback read-feedback %)
                        :delete-fn #(write-store :feedback delete-feedback %)
                        :entity-type :feedback
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
