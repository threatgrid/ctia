(ns ctia.http.routes.judgement
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :refer [realize-feedback realize-judgement]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :refer [paginated-ok PagingParams]]
   [ctia.properties :refer [properties]]
   [ctia.store :refer :all]
   [ctim.domain.id :as domain-id]
   [ctim.schemas
    [feedback :refer [NewFeedback StoredFeedback]]
    [judgement :refer [NewJudgement StoredJudgement]]
    [relationships :as rel]]
   [ring.util.http-response :refer :all]
   [schema.core :as s]
   [schema-tools.core :as st]))

(s/defschema FeedbacksByJudgementQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :feedback :reason)}))

(def ->id
  (domain-id/long-id-factory :judgement
                             #(get-in @properties [:ctia :http :show])))

(defroutes judgement-routes
  (context "/judgement" []
    :tags ["Judgement"]
    (POST "/" []
      :return StoredJudgement
      :body [judgement NewJudgement {:description "a new Judgement"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Adds a new Judgement"
      :capabilities :create-judgement
      :identity identity
      (created (flows/create-flow :realize-fn realize-judgement
                                  :store-fn #(write-store :judgement create-judgement %)
                                  :entity-type :judgement
                                  :identity identity
                                  :entity judgement)))
    (POST "/:judgement-id/indicator" []
      :return (s/maybe rel/RelatedIndicator)
      :path-params [judgement-id :- s/Str]
      :body [indicator-relationship rel/RelatedIndicator]
      :header-params [api_key :- s/Str]
      :summary "Adds an Indicator to a Judgement"
      :capabilities :create-judgement
      (if-let [d (write-store :judgement
                              add-indicator-to-judgement
                              judgement-id
                              indicator-relationship)]
        (ok d)
        (not-found)))
    (GET "/:id" []
      :return (s/maybe StoredJudgement)
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Gets a Judgement by ID"
      :capabilities :read-judgement
      (if-let [d (read-store :judgement read-judgement id)]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Deletes a Judgement"
      :capabilities :delete-judgement
      :identity identity
      (if (flows/delete-flow :get-fn #(read-store :judgement read-judgement %)
                             :delete-fn #(write-store :judgement delete-judgement %)
                             :entity-type :judgement
                             :entity-id id
                             :identity identity)
        (no-content)
        (not-found)))))
