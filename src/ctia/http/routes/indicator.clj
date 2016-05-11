(ns ctia.http.routes.indicator
  (:require [compojure.api.sweet :refer :all]
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

(s/defschema SightingsByIndicatorQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :timestamp :description :source :confidence)}))

(defroutes indicator-routes
  (context "/indicator" []
    :tags ["Indicator"]
    (GET "/:id/judgements" []
      :return [StoredJudgement]
      :path-params [id :- Long]
      :summary "Gets all Judgements associated with the Indicator"
      (not-found))
    (GET "/:id/campaigns" []
      :return [StoredCampaign]
      :path-params [id :- s/Str]
      :query [params SightingsByIndicatorQueryParams]
      :summary "Gets all Campaigns associated with the Indicator"
      (if-let [indicator (read-indicator @indicator-store id)]
        (if-let [campaigns (list-campaigns-by-indicators @campaign-store [indicator] params)]
          (paginated-ok campaigns)
          (not-found))
        (not-found)))
    (GET "/:id/coas" []
      :return [StoredCOA]
      :path-params [id :- s/Str]
      :query [params SightingsByIndicatorQueryParams]
      :summary "Gets all COAs associated with the Indicator"
      (if-let [indicator (read-indicator @indicator-store id)]
        (if-let [coas (list-coas-by-indicators @coa-store [indicator] params)]
          (paginated-ok coas)
          (not-found))
        (not-found)))
    (GET "/:id/ttps" []
      :return [StoredTTP]
      :path-params [id :- s/Str]
      :query [params SightingsByIndicatorQueryParams]
      :summary "Gets all TTPs associated with the Indicator"
      (if-let [indicator (read-indicator @indicator-store id)]
        (if-let [ttps (list-ttps-by-indicators @ttp-store [indicator] params)]
          (paginated-ok ttps)
          (not-found))
        (not-found)))
    (POST "/" []
      :return StoredIndicator
      :body [indicator NewIndicator {:description "a new Indicator"}]
      :summary "Adds a new Indicator"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:create-indicator :admin}
      :login login
      (ok (flows/create-flow :realize-fn realize-indicator
                             :store-fn #(create-indicator @indicator-store %)
                             :entity-type :indicator
                             :login login
                             :entity indicator)))
    (PUT "/:id" []
      :return StoredIndicator
      :body [indicator NewIndicator {:description "an updated Indicator"}]
      :summary "Updates an Indicator"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:create-indicator :admin}
      :login login
      (ok (flows/update-flow :get-fn #(read-indicator @indicator-store %)
                             :realize-fn realize-indicator
                             :update-fn #(update-indicator @indicator-store (:id %) %)
                             :entity-type :indicator
                             :id id
                             :login login
                             :entity indicator)))
    (GET "/:id" []
      :return (s/maybe StoredIndicator)
      :summary "Gets an Indicator by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:read-indicator :admin}
      ;; :description "This is a little decription"
      ;; :query-params [{offset :-  Long {:summary "asdads" :default 0}}
      ;;                {limit :-  Long 0}
      ;;                {after :-  Time nil}
      ;;                {before :-  Time nil}
      ;;                {sort_by :- IndicatorSort "timestamp"}
      ;;                {sort_order :- SortOrder "desc"}
      ;;                {source :- s/Str nil}
      ;;                {observable :- ObservableType nil}]
      (if-let [d (read-indicator @indicator-store id)]
        (ok d)
        (not-found)))
    (GET "/:id/sightings" []
      :return [StoredSighting]
      :path-params [id :- s/Str]
      :query [params SightingsByIndicatorQueryParams]
      :summary "Gets all Sightings associated with the Indicator"
      (if-let [indicator (read-indicator @indicator-store id)]
        (if-let [sightings (list-sightings-by-indicators @sighting-store [indicator] params)]
          (paginated-ok sightings)
          (not-found))
        (not-found)))
    (GET "/title/:title" []
      :return (s/maybe [StoredIndicator])
      :summary "Gets an Indicator by title"
      :query [params IndicatorsByTitleQueryParams]
      :path-params [title :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:list-indicators-by-title :admin}

      (paginated-ok
       (list-indicators @indicator-store {:title title} params)))))
