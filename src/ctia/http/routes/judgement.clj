(ns ctia.http.routes.judgement
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.judgement :refer [NewJudgement
                                            StoredJudgement
                                            realize-judgement]]

            [ctia.schemas.feedback :refer [NewFeedback
                                           StoredFeedback
                                           realize-feedback]]))

(defroutes judgement-routes

  (context "/judgement" []
    :tags ["Judgement"]
    (POST "/" []
      :return StoredJudgement
      :body [judgement NewJudgement {:description "a new Judgement"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Adds a new Judgement"
      :capabilities #{:create-judgement :admin}
      :login login
      (ok (flows/create-flow :model StoredJudgement
                             :realize-fn realize-judgement
                             :store-fn #(create-judgement @judgement-store %)
                             :object-type :judgement
                             :login login
                             :object judgement)))
    (POST "/:judgement-id/feedback" []
      :tags ["Feedback"]
      :return StoredFeedback
      :path-params [judgement-id :- s/Str]
      :body [feedback NewFeedback {:description "a new Feedback on a Judgement"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Adds a Feedback to a Judgement"
      :capabilities #{:create-feedback :admin}
      :login login
      (ok (flows/create-flow :model StoredFeedback
                             :realize-fn #(realize-feedback %1 %2 %3 judgement-id)
                             :store-fn #(create-feedback @feedback-store %)
                             :object-type :feedback
                             :login login
                             :object feedback)))
    (GET "/:judgement-id/feedback" []
      :tags ["Feedback"]
      :return (s/maybe [StoredFeedback])
      :path-params [judgement-id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:read-feedback :admin}
      :summary "Gets all Feedback for this Judgement."
      (if-let [d (list-feedback @feedback-store {:judgement judgement-id})]
        (ok d)
        (not-found)))
    (POST "/:judgement-id/indicator" []
      :return (s/maybe rel/RelatedIndicator)
      :path-params [judgement-id :- s/Str]
      :body [indicator-relationship rel/RelatedIndicator]
      :header-params [api_key :- s/Str]
      :summary "Adds an Indicator to a Judgement"
      :capabilities #{:create-judgement-indicator}
      (if-let [d (add-indicator-to-judgement @judgement-store
                                             judgement-id
                                             indicator-relationship)]
        (ok d)
        (not-found)))
    (GET "/:id" []
      :return (s/maybe StoredJudgement)
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Gets a Judgement by ID"
      :capabilities #{:read-judgement :admin}
      (if-let [d (read-judgement @judgement-store id)]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Deletes a Judgement"
      :capabilities #{:delete-judgement :admin}
      (if (flows/delete-flow :model StoredJudgement
                             :get-fn #(read-judgement @judgement-store %)
                             :delete-fn #(delete-judgement @judgement-store %)
                             :object-type :judgement
                             :id id)
        (no-content)
        (not-found)))))
