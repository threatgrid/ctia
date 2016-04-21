(ns ctia.http.routes.indicator
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.schemas.judgement :refer [StoredJudgement]]
            [ctia.schemas.campaign :refer [StoredCampaign]]
            [ctia.schemas.coa :refer [StoredCOA]]
            [ctia.schemas.ttp :refer [StoredTTP]]
            [ctia.schemas.indicator :refer [NewIndicator
                                            StoredIndicator
                                            realize-indicator
                                            generalize-indicator]]
            [ctia.schemas.sighting :refer [NewSighting
                                           StoredSighting
                                           realize-sighting]]))

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
      :path-params [id :- Long]
      :summary "Gets all Campaigns associated with the Indicator"
      (not-found))
    (GET "/:id/coas" []
      :tags ["COA"]
      :return [StoredCOA]
      :path-params [id :- Long]
      :summary "Gets all COAs associated with the Indicator"
      (not-found))
    (GET "/:id/ttps" []
      :tags ["TTP"]
      :return [StoredTTP]
      :path-params [id :- Long]
      :summary "Gets all TTPs associated with the Indicator"
      (not-found))
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
    (GET "/title/:title" []
      :return (s/maybe [StoredIndicator])
      :summary "Gets an Indicator by title"
      :path-params [title :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:list-indicators-by-title :admin}
      (if-let [d (list-indicators @indicator-store {:title title})]
        (ok d)
        (not-found)))))
