(ns ctia.http.routes.judgement
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.id :as id]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common :refer [paginated-ok PagingParams]]
            [ctia.properties :refer [properties]]
            [ctia.schemas
             [feedback :refer [NewFeedback realize-feedback StoredFeedback]]
             [judgement :refer [NewJudgement realize-judgement StoredJudgement]]
             [relationships :as rel]]
            [ctia.store :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema FeedbacksByJudgementQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :feedback :reason)}))

(def ->id
  (id/long-id-factory :judgement
                      #(get-in @properties [:ctia :http :show])))

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
      (ok (flows/create-flow :realize-fn realize-judgement
                             :store-fn #(create-judgement @judgement-store %)
                             :entity-type :judgement
                             :login login
                             :entity judgement)))
    (POST "/:judgement-id/feedback" []
      :tags ["Feedback"]
      :return StoredFeedback
      :path-params [judgement-id :- s/Str]
      :body [feedback NewFeedback {:description "a new Feedback on a Judgement"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Adds a Feedback to a Judgement"
      :capabilities #{:create-feedback :admin}
      :login login
      (ok (flows/create-flow :realize-fn #(realize-feedback %1 %2 %3 (->id judgement-id))
                             :store-fn #(create-feedback @feedback-store %)
                             :entity-type :feedback
                             :login login
                             :entity feedback)))
    (GET "/:judgement-id/feedback" []
      :tags ["Feedback"]
      :return (s/maybe [StoredFeedback])
      :query [params FeedbacksByJudgementQueryParams]
      :path-params [judgement-id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:read-feedback :admin}
      :summary "Gets all Feedback for this Judgement."
      (if-let [d (list-feedback @feedback-store {:judgement (->id judgement-id)} params)]
        (paginated-ok d)
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
      :login login
      (if (flows/delete-flow :get-fn #(read-judgement @judgement-store %)
                             :delete-fn #(delete-judgement @judgement-store %)
                             :entity-type :judgement
                             :id id
                             :login login)
        (no-content)
        (not-found)))))
