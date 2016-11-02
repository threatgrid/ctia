(ns ctia.http.routes.indicator
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities
             :refer [->long-id realize-indicator realize-sighting]]
            [ctia.domain.entities
             [indicator :refer [with-long-id page-with-long-id]]
             [sighting :as sighting]]
            [ctia.flows.crud :as f]
            [ctia.http.routes.common
             :refer [created PagingParams paginated-ok]]
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
   {(s/optional-key :sort_by) (s/enum :id :title)}))

(s/defschema IndicatorsListQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :title)}))

(s/defschema IndicatorsByExternalIdQueryParams
  (st/merge
   PagingParams
   {:external_id s/Str
    (s/optional-key :sort_by) (s/enum :id :title)}))

(s/defschema SightingsByIndicatorQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :timestamp :description :source :confidence)}))

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
         (f/pop-result
          (f/create-flow :realize-fn realize-indicator
                         :store-fn #(write-store :indicator create-indicator %)
                         :entity-type :indicator
                         :identity identity
                         :entity indicator)))))
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
         (f/pop-result
          (f/update-flow :get-fn #(read-store :indicator read-indicator %)
                         :realize-fn realize-indicator
                         :update-fn #(write-store :indicator update-indicator id %)
                         :entity-type :indicator
                         :entity-id id
                         :identity identity
                         :entity indicator)))))

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
