(ns ctia.http.routes.feedback
  (:require
    [compojure.api.sweet :refer :all]
            [ctia.domain.entities :refer [realize-feedback]]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common :refer [paginated-ok PagingParams]]
            [ctia.store :refer :all]
            [ctim.schemas.feedback :refer [NewFeedback StoredFeedback]]
            [ring.util.http-response :refer :all]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defschema FeedbackQueryParams
  (st/merge
   PagingParams
   {:entity_id s/Str
    (s/optional-key :sort_by) (s/enum :id :feedback :reason)}))

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
      (ok (flows/create-flow :realize-fn realize-feedback
                             :store-fn #(write-store :feedback
                                                     (fn [s] (create-feedback s %)))
                             :entity-type :feedback
                             :identity identity
                             :entity feedback)))
    (GET "/" []
      :return [StoredFeedback]
      :query [params FeedbackQueryParams]
      :summary "Search Feedback"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-feedback

      (paginated-ok
       (read-store :feedback
                   (fn [store] (list-feedback store
                                             (select-keys params [:entity_id])
                                             (dissoc params :entity_id))))))
    (GET "/:id" []
      :return (s/maybe StoredFeedback)
      :summary "Gets a Feedback by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-feedback
      (if-let [d (read-store :feedback
                             (fn [s] (read-feedback s id)))]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a feedback"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-feedback
      :identity identity
      (if (flows/delete-flow :get-fn #(read-store :feedback
                                                  (fn [s] (read-feedback s %)))
                             :delete-fn #(write-store :feedback
                                                      (fn [s] (delete-feedback s %)))
                             :entity-type :feedback
                             :entity-id id
                             :identity identity)
        (no-content)
        (not-found)))))
