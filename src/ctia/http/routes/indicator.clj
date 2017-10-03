(ns ctia.http.routes.indicator
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities :as ent]
            [ctia.domain.entities
             [indicator :refer [with-long-id page-with-long-id]]
             [sighting :as sighting]]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common
             :refer [created PagingParams paging-param-keys paginated-ok
                     BaseEntityFilterParams SourcableEntityFilterParams
                     IndicatorSearchParams
                     indicator-sort-fields
                     sighting-sort-fields]]
            [ctia.store :refer :all]
            [ring.util.http-response :refer [ok no-content not-found]]
            [schema.core :as s]
            [schema-tools.core :as st]
            [ctia.schemas.core :refer [NewIndicator Indicator]]))

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
                 :return Indicator
                 :body [indicator NewIndicator {:description "a new Indicator"}]
                 :summary "Adds a new Indicator"
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :capabilities :create-indicator
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :realize-fn ent/realize-indicator
                      :store-fn #(write-store :indicator
                                              create-indicators
                                              %
                                              identity-map)
                      :long-id-fn with-long-id
                      :entity-type :indicator
                      :identity identity
                      :entities [indicator]
                      :spec :new-indicator/map)
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return Indicator
                :body [indicator NewIndicator {:description "an updated Indicator"}]
                :summary "Updates an Indicator"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :create-indicator
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow
                     :get-fn #(read-store :indicator
                                          read-indicator
                                          %
                                          identity-map)
                     :realize-fn ent/realize-indicator
                     :update-fn #(write-store :indicator
                                              update-indicator
                                              (:id %)
                                              %
                                              identity-map)
                     :long-id-fn with-long-id
                     :entity-type :indicator
                     :entity-id id
                     :identity identity
                     :entity indicator
                     :spec :new-indicator/map)
                    ent/un-store
                    ok))

           ;; MORE TESTS, INCLUDING FILTER MAP TEST
           ;; ADD TO OTHER ENTITIES
           (GET "/search" []
                :return (s/maybe [Indicator])
                :summary "Search for an indicator using a Lucene/ES query string"
                :query [params IndicatorSearchParams]
                :capabilities #{:read-indicator :search-indicator}
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :identity identity
                :identity-map identity-map
                (-> (query-string-search-store
                     :indicator
                     query-string-search
                     (:query params)
                     (dissoc params :query :sort_by :sort_order :offset :limit)
                     identity-map
                     (select-keys params [:sort_by :sort_order :offset :limit]))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/external_id/:external_id" []
                :return [(s/maybe Indicator)]
                :query [q IndicatorsByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List Indicators by external id"
                :capabilities #{:read-indicator :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :indicator
                                list-indicators
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe Indicator)
                :summary "Gets an Indicator by ID"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-indicator
                :identity identity
                :identity-map identity-map
                (if-let [indicator (read-store :indicator
                                               read-indicator
                                               id
                                               identity-map)]
                  (-> indicator
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))))
