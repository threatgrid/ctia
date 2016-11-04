(ns ctia.http.routes.judgement
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :refer [realize-feedback realize-judgement]]
   [ctia.domain.entities.judgement :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created paginated-ok PagingParams JudgementSearchParams
            feedback-sort-fields
            judgement-sort-fields]]
   [ctia.properties :refer [get-http-show]]
   [ctia.store :refer :all]
   [ctim.domain.id :as id]
   [ring.util.http-response :refer [ok no-content not-found]]
   [schema.core :as s]
   [schema-tools.core :as st]
   [ctia.schemas.core :refer [NewFeedback
                              StoredFeedback
                              NewJudgement
                              StoredJudgement
                              RelatedIndicator]]))


(s/defschema FeedbacksByJudgementQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) feedback-sort-fields}))

(s/defschema JudgementsQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) judgement-sort-fields}))

(s/defschema JudgementsByExternalIdQueryParams
  (st/merge PagingParams
            {:external_id s/Str
             (s/optional-key :sort_by) judgement-sort-fields}))

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
                 (created
                  (with-long-id
                    (first
                     (flows/create-flow
                      :realize-fn realize-judgement
                      :store-fn #(write-store :judgement create-judgements %)
                      :entity-type :judgement
                      :identity identity
                      :entities [judgement])))))
           (POST "/:judgement-id/indicator" []
                 :return (s/maybe RelatedIndicator)
                 :path-params [judgement-id :- s/Str]
                 :body [indicator-relationship RelatedIndicator]
                 :header-params [api_key :- s/Str]
                 :summary "Adds an Indicator to a Judgement"
                 :capabilities :create-judgement
                 (if-let [d (write-store :judgement
                                         add-indicator-to-judgement
                                         judgement-id
                                         indicator-relationship)]
                   (ok d)
                   (not-found)))

           (POST "/:judgement-id/indicator" []
                 :return (s/maybe RelatedIndicator)
                 :path-params [judgement-id :- s/Str]
                 :body [indicator-relationship RelatedIndicator]
                 :header-params [api_key :- s/Str]
                 :summary "Adds an Indicator to a Judgement"
                 :capabilities :create-judgement
                 (if-let [d (write-store :judgement
                                         add-indicator-to-judgement
                                         judgement-id
                                         indicator-relationship)]
                   (ok d)
                   (not-found)))
           
           (GET "/search" []
                :return (s/maybe [StoredJudgement])
                :summary "Search for a Judgement using a Lucene/ES query string"
                :query [params JudgementSearchParams]
                :capabilities #{:read-judgement :search-judgement}
                :header-params [api_key :- (s/maybe s/Str)]
                (paginated-ok
                 (page-with-long-id
                  (query-string-search-store :judgement
                                             query-string-search
                                             (:query params)
                                             (dissoc params :query :sort_by :sort_order :offset :limit)
                                             (select-keys params [:sort_by :sort_order :offset :limit])))))
           
           
           (GET "/external_id" []
                :return [(s/maybe StoredJudgement)]
                :query [q JudgementsByExternalIdQueryParams]
                
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "Get Judgements by external ids"
                :capabilities #{:read-judgement :external-id}
                (paginated-ok
                 (page-with-long-id
                  (read-store :judgement
                              list-judgements
                              {:external_ids (:external_id q)}
                              q))))
           
           (GET "/:id" []
                :return (s/maybe StoredJudgement)
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "Gets a Judgement by ID"
                :capabilities :read-judgement
                (if-let [d (read-store :judgement read-judgement id)]
                  (ok (with-long-id d))
                  (not-found)))
           

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :header-params [api_key :- (s/maybe s/Str)]
                   :summary "Deletes a Judgement"
                   :capabilities :delete-judgement
                   :identity identity
                   (if (flows/delete-flow
                        :get-fn #(read-store :judgement read-judgement %)
                        :delete-fn #(write-store :judgement delete-judgement %)
                        :entity-type :judgement
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
