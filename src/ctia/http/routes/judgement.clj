(ns ctia.http.routes.judgement
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
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
   [ctia.schemas.core :refer [NewJudgement Judgement]]))

(s/defschema FeedbacksByJudgementQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) feedback-sort-fields}))

(s/defschema JudgementsQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) judgement-sort-fields}))

(s/defschema JudgementsByExternalIdQueryParams
  JudgementsQueryParams)

(defroutes judgement-routes
  (context "/judgement" []
           :tags ["Judgement"]
           (POST "/" []
                 :return Judgement
                 :body [judgement NewJudgement {:description "a new Judgement"}]
                 :header-params [api_key :- (s/maybe s/Str)]
                 :summary "Adds a new Judgement"
                 :capabilities :create-judgement
                 :identity identity
                 (-> (flows/create-flow
                      :realize-fn ent/realize-judgement
                      :store-fn #(write-store :judgement create-judgements %)
                      :long-id-fn with-long-id
                      :entity-type :judgement
                      :identity identity
                      :entities [judgement])
                     first
                     ent/un-store
                     created))

           (GET "/search" []
                :return (s/maybe [Judgement])
                :summary "Search for a Judgement using a Lucene/ES query string"
                :query [params JudgementSearchParams]
                :capabilities #{:read-judgement :search-judgement}
                :header-params [api_key :- (s/maybe s/Str)]
                (-> (query-string-search-store
                     :judgement
                     query-string-search
                     (:query params)
                     (dissoc params :query :sort_by :sort_order :offset :limit)
                     (select-keys params [:sort_by :sort_order :offset :limit]))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/external_id/:external_id" []
                :return [(s/maybe Judgement)]
                :query [q JudgementsByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "Get Judgements by external ids"
                :capabilities #{:read-judgement :external-id}
                (-> (read-store :judgement
                                list-judgements
                                {:external_ids external_id}
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe Judgement)
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "Gets a Judgement by ID"
                :capabilities :read-judgement
                (if-let [judgement (read-store :judgement read-judgement id)]
                  (-> judgement
                      with-long-id
                      ent/un-store
                      ok)
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
