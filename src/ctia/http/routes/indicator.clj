(ns ctia.http.routes.indicator
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.id :as id]
            [ctia.properties :refer [properties]]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common :refer [PagingParams paginated-ok]]
            [ctia.schemas
             [campaign :refer [StoredCampaign]]
             [coa :refer [StoredCOA]]
             [indicator :refer [generalize-indicator
                                NewIndicator
                                realize-indicator
                                StoredIndicator]]
             [judgement :refer [StoredJudgement]]
             [sighting :refer [NewSighting
                               realize-sighting
                               StoredSighting]]
             [ttp :refer [StoredTTP]]]
            [ctia.store :refer :all]
            [ring.util.http-response :refer :all]
            [schema-tools.core :as st]
            [schema.core :as s]))


(s/defschema IndicatorsByTitleQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :title)}))

(s/defschema IndicatorsListQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :title)}))

(s/defschema SightingsByIndicatorQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :timestamp :description :source :confidence)}))

(defn ->long-id [id-type short-id]
  (id/long-id
   (id/short-id->id id-type
                    short-id
                    #(get-in @properties [:ctia :http :show]))))

(defroutes indicator-routes
  (context "/indicator" []
    :tags ["Indicator"]
    (POST "/" []
      :return StoredIndicator
      :body [indicator NewIndicator {:description "a new Indicator"}]
      :summary "Adds a new Indicator"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-indicator
      :login login
      (ok (flows/create-flow :realize-fn realize-indicator
                             :store-fn #(write-store :indicator
                                                     (fn [s] (create-indicator s %)))
                             :entity-type :indicator
                             :login login
                             :entity indicator)))
    (PUT "/:id" []
      :return StoredIndicator
      :body [indicator NewIndicator {:description "an updated Indicator"}]
      :summary "Updates an Indicator"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-indicator
      :login login
      (ok (flows/update-flow :get-fn #(read-store :indicator
                                                  (fn [s] (read-indicator s %)))
                             :realize-fn realize-indicator
                             :update-fn #(write-store :indicator
                                                      (fn [s] (update-indicator s (:id %) %)))
                             :entity-type :indicator
                             :id id
                             :login login
                             :entity indicator)))
    (GET "/:id" []
      :return (s/maybe StoredIndicator)
      :summary "Gets an Indicator by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-indicator
      (if-let [d (read-store :indicator
                             (fn [s] (read-indicator s id)))]
        (ok d)
        (not-found)))
    (GET "/:id/sightings" []
      :return [StoredSighting]
      :path-params [id :- s/Str]
      :query [params SightingsByIndicatorQueryParams]
      :summary "Gets all Sightings associated with the Indicator"
      :capabilities #{:read-indicator :list-sightings}
      (if-let [indicator (read-store :indicator
                                     (fn [s] (read-indicator s id)))]
        (if-let [sightings (read-store :sighting
                                       (fn [s] (list-sightings-by-indicators s [indicator] params)))]
          (paginated-ok sightings)
          (not-found))
        (not-found)))
    (GET "/title/:title" []
      :return (s/maybe [StoredIndicator])
      :summary "Gets an Indicator by title"
      :query [params IndicatorsByTitleQueryParams]
      :path-params [title :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-indicator
      (paginated-ok
       (read-store :indicator
                   (fn [s] (list-indicators s {:title title} params))))))
  (GET "/judgement/:id/indicators" []
    :tags ["Indicator"]
    :return (s/maybe [StoredIndicator])
    :summary "Gets all indicators referencing some judgement"
    :query [params IndicatorsListQueryParams]
    :path-params [id :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-indicators
    (paginated-ok
     (read-store :indicator
                 (fn [store] (list-indicators store {:judgements #{{:judgement_id (->long-id :judgement id)}}} params)))))
  (GET "/campaign/:id/indicators" []
    :tags ["Indicator"]
    :return (s/maybe [StoredIndicator])
    :summary "Gets all indicators related to a campaign"
    :query [params IndicatorsListQueryParams]
    :path-params [id :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-indicators
    (paginated-ok
     (read-store :indicator (fn [store] (list-indicators store
                                                        {:related_campaigns #{{:campaign_id (->long-id :campaign id)}}}
                                                        params)))))
  (GET "/coa/:id/indicators" []
    :tags ["Indicator"]
    :return (s/maybe [StoredIndicator])
    :summary "Gets all indicators related to a coa"
    :query [params IndicatorsListQueryParams]
    :path-params [id :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-indicators
    (paginated-ok
     (read-store :indicator
                 (fn [store] (list-indicators store
                                             {:related_COAs #{{:COA_id (->long-id :coa id)}}}
                                             params)))))
  (GET "/ttp/:id/indicators" []
    :tags ["Indicator"]
    :return (s/maybe [StoredIndicator])
    :summary "Gets all indicators indicating a TTP"
    :query [params IndicatorsListQueryParams]
    :path-params [id :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-indicators
    (paginated-ok
     (read-store :indicator
                 (fn [store] (list-indicators store
                                             {:indicated_TTP #{{:ttp_id (->long-id :ttp id)}}}
                                             params)))))
  (GET "/indicator/:id/indicators" []
    :tags ["Indicator"]
    :return (s/maybe [StoredIndicator])
    :summary "Gets all indicators related to another indicator"
    :query [params IndicatorsListQueryParams]
    :path-params [id :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-indicators
    (paginated-ok
     (read-store :indicator
                 (fn [store] (list-indicators store
                                             {:related_indicators #{{:indicator_id (->long-id :indicator id)}}}
                                             params))))))
