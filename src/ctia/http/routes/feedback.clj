(ns ctia.http.routes.feedback
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.feedback :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created
            paginated-ok
            PagingParams]]
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
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :realize-fn ent/realize-feedback
                      :store-fn #(write-store :feedback
                                              create-feedbacks
                                              %
                                              identity-map)
                      :long-id-fn with-long-id
                      :entity-type :feedback
                      :identity identity
                      :identity-map identity-map
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
                :identity identity
                :identity-map identity-map
                (-> (read-store :feedback
                                list-feedback
                                (select-keys params [:entity_id])
                                identity-map
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
                :identity identity
                :identity-map identity-map
                (-> (read-store :feedback
                                list-feedback
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe Feedback)
                :summary "Gets a Feedback by ID"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-feedback
                :identity identity
                :identity-map identity-map
                (if-let [feedback (read-store :feedback
                                              read-feedback
                                              id
                                              identity-map)]
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
                   :identity-map identity-map
                   (if (flows/delete-flow
                        :get-fn #(read-store :feedback
                                             read-feedback
                                             %
                                             identity-map)
                        :delete-fn #(write-store :feedback
                                                 delete-feedback
                                                 %
                                                 identity-map)
                        :entity-type :feedback
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
