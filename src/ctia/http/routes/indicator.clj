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


(s/defschema IndicatorsByTitleQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) indicator-sort-fields}))

(s/defschema IndicatorsListQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) indicator-sort-fields}))

(s/defschema IndicatorsByExternalIdQueryParams
  (st/merge
   PagingParams
   {:external_id s/Str
    (s/optional-key :sort_by) indicator-sort-fields}))

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
       (with-long-id
         (first
          (flows/create-flow :realize-fn realize-indicator
                             :store-fn #(write-store :indicator create-indicators %)
                             :entity-type :indicator
                             :identity identity
                             :entities [indicator])))))
    (PUT "/:id" []
      :return StoredIndicator
      :body [indicator NewIndicator {:description "an updated Indicator"}]
      :summary "Updates an Indicator"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-indicator
      :identity identity
      (ok
       (with-long-id
         (flows/update-flow :get-fn #(read-store :indicator read-indicator %)
                            :realize-fn realize-indicator
                            :update-fn #(write-store :indicator update-indicator (:id %) %)
                            :entity-type :indicator
                            :entity-id id
                            :identity identity
                            :entity indicator))))
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
           (query-string-search-store :indicator
                       query-string-search
                       (:query params)
                       (dissoc params :query :sort_by :sort_order :offset :limit)
                       (select-keys params [:sort_by :sort_order :offset :limit])))))


    
    (GET "/external_id" []
      :return [(s/maybe StoredIndicator)]
      :query [q IndicatorsByExternalIdQueryParams]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "List Indicators by external id"
      :capabilities #{:read-indicator :external-id}
      (paginated-ok
       (page-with-long-id
        (read-store :indicator list-indicators
                    {:external_ids (:external_id q)} q))))
    
    (GET "/:id" []
      :return (s/maybe StoredIndicator)
      :summary "Gets an Indicator by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-indicator
      (if-let [d (read-store :indicator read-indicator id)]
        (ok (with-long-id d))
        (not-found)))

    (GET "/:id/sightings" []
      :return [StoredSighting]
      :path-params [id :- s/Str]
      :query [params SightingsByIndicatorQueryParams]
      :summary "Gets all Sightings associated with the Indicator"
      :capabilities #{:read-indicator :list-sightings}
      (if-let [indicator (read-store :indicator read-indicator id)]
        (paginated-ok
         (sighting/page-with-long-id
          (read-store :sighting
                      list-sightings
                      {:indicators #{{:indicator_id (->long-id :indicator id)}}}
                      params)))
        (not-found)))

    (GET "/title/:title" []
      :return (s/maybe [StoredIndicator])
      :summary "Gets an Indicator by title"
      :query [params IndicatorsByTitleQueryParams]
      :path-params [title :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-indicator
      (paginated-ok
       (page-with-long-id
         (read-store :indicator list-indicators {:title title} params)))))

  (GET "/judgement/:id/indicators" []
    :tags ["Indicator"]
    :return (s/maybe [StoredIndicator])
    :summary "Gets all indicators referencing some judgement"
    :query [params IndicatorsListQueryParams]
    :path-params [id :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-indicators
    (paginated-ok
     (page-with-long-id
      (read-store :indicator
                  list-indicators
                  {:judgements #{{:judgement_id (->long-id :judgement id)}}}
                  params))))

  (GET "/campaign/:id/indicators" []
    :tags ["Indicator"]
    :return (s/maybe [StoredIndicator])
    :summary "Gets all indicators related to a campaign"
    :query [params IndicatorsListQueryParams]
    :path-params [id :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-indicators
    (paginated-ok
     (page-with-long-id
      (read-store :indicator
                  list-indicators
                  {:related_campaigns #{{:campaign_id (->long-id :campaign id)}}}
                  params))))

  (GET "/coa/:id/indicators" []
    :tags ["Indicator"]
    :return (s/maybe [StoredIndicator])
    :summary "Gets all indicators related to a coa"
    :query [params IndicatorsListQueryParams]
    :path-params [id :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-indicators
    (paginated-ok
     (page-with-long-id
      (read-store :indicator
                  list-indicators
                  {:related_COAs #{{:COA_id (->long-id :coa id)}}}
                  params))))

  (GET "/ttp/:id/indicators" []
    :tags ["Indicator"]
    :return (s/maybe [StoredIndicator])
    :summary "Gets all indicators indicating a TTP"
    :query [params IndicatorsListQueryParams]
    :path-params [id :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-indicators
    (paginated-ok
     (page-with-long-id
      (read-store :indicator
                  list-indicators
                  {:indicated_TTP #{{:ttp_id (->long-id :ttp id)}}}
                  params))))

  (GET "/indicator/:id/indicators" []
    :tags ["Indicator"]
    :return (s/maybe [StoredIndicator])
    :summary "Gets all indicators related to another indicator"
    :query [params IndicatorsListQueryParams]
    :path-params [id :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-indicators
    (paginated-ok
     (page-with-long-id
      (read-store :indicator
                  list-indicators
                  {:related_indicators #{{:indicator_id (->long-id :indicator id)}}}
                  params)))))
