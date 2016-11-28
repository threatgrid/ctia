(ns ctia.http.routes.indicator
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities
             :refer [->long-id realize-indicator realize-sighting]]
            [ctia.domain.entities
             [indicator :refer [with-long-id page-with-long-id]]
             [sighting :as sighting]]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common
             :refer [created PagingParams paging-param-keys paginated-ok
                     BaseEntityFilterParams SourcableEntityFilterParams
                     IndicatorSearchParams
                     indicator-sort-fields sighting-sort-fields]]
            [ctia.store :refer :all]
            [ring.util.http-response :refer [ok no-content not-found]]
            [schema.core :as s]
            [schema-tools.core :as st]
            [ctia.schemas.core :refer [StoredCampaign
                                       StoredCOA
                                       NewIndicator
                                       StoredIndicator
                                       StoredJudgement
                                       NewSighting
                                       StoredSighting
                                       StoredTTP]]))

(s/defschema IndicatorsListQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) indicator-sort-fields}))

(s/defschema IndicatorsByExternalIdQueryParams
  IndicatorsListQueryParams)

(s/defschema SightingsByIndicatorQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) sighting-sort-fields}))

(defroutes indicator-routes
  (context "/indicator" []
           :tags ["Indicator"]
           (POST "/" []
                 :return StoredIndicator
                 :body [indicator NewIndicator {:description "a new Indicator"}]
                 :summary "Adds a new Indicator"
                 :header-params [api_key :- (s/maybe s/Str)]
                 :capabilities :create-indicator
                 :identity identity
                 (created
                  (first
                   (flows/create-flow
                    :realize-fn realize-indicator
                    :store-fn #(write-store :indicator create-indicators %)
                    :long-id-fn with-long-id
                    :entity-type :indicator
                    :identity identity
                    :entities [indicator]))))

           (PUT "/:id" []
                :return StoredIndicator
                :body [indicator NewIndicator {:description "an updated Indicator"}]
                :summary "Updates an Indicator"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :create-indicator
                :identity identity
                (ok
                 (flows/update-flow
                  :get-fn #(read-store :indicator read-indicator %)
                  :realize-fn realize-indicator
                  :update-fn #(write-store :indicator update-indicator (:id %) %)
                  :long-id-fn with-long-id
                  :entity-type :indicator
                  :entity-id id
                  :identity identity
                  :entity indicator)))

           ;; MORE TESTS, INCLUDING FILTER MAP TEST
           ;; ADD TO OTHER ENTITIES
           (GET "/search" []
                :return (s/maybe [StoredIndicator])
                :summary "Search for an indicator using a Lucene/ES query string"
                :query [params IndicatorSearchParams]
                :capabilities #{:read-indicator :search-indicator}
                :header-params [api_key :- (s/maybe s/Str)]
                (paginated-ok
                 (page-with-long-id
                  (query-string-search-store
                   :indicator
                   query-string-search
                   (:query params)
                   (dissoc params :query :sort_by :sort_order :offset :limit)
                   (select-keys params [:sort_by :sort_order :offset :limit])))))



           (GET "/external_id/:external_id" []
                :return [(s/maybe StoredIndicator)]
                :query [q IndicatorsByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "List Indicators by external id"
                :capabilities #{:read-indicator :external-id}
                (paginated-ok
                 (page-with-long-id
                  (read-store :indicator list-indicators
                              {:external_ids external_id} q))))

           (GET "/:id" []
                :return (s/maybe StoredIndicator)
                :summary "Gets an Indicator by ID"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :read-indicator
                (if-let [d (read-store :indicator read-indicator id)]
                  (ok (with-long-id d))
                  (not-found)))))
