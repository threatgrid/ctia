(ns ctia.http.routes.campaign
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities :refer [realize-campaign]]
            [ctia.flows.crud :as flows]
            [ctim.schemas.campaign :refer [NewCampaign StoredCampaign]]
            [ctia.store :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defroutes campaign-routes
  (context "/campaign" []
    :tags ["Campaign"]
    (POST "/" []
      :return StoredCampaign
      :body [campaign NewCampaign {:description "a new campaign"}]
      :summary "Adds a new Campaign"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-campaign
      :identity identity
      (created (flows/create-flow :realize-fn realize-campaign
                                  :store-fn #(write-store :campaign create-campaign %)
                                  :entity-type :campaign
                                  :identity identity
                                  :entity campaign)))
    (PUT "/:id" []
      :return StoredCampaign
      :body [campaign NewCampaign {:description "an updated campaign"}]
      :summary "Updates a Campaign"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-campaign
      :identity identity
      (ok (flows/update-flow :get-fn #(read-store :campaign read-campaign %)
                             :realize-fn realize-campaign
                             :update-fn #(write-store :campaign update-campaign (:id %) %)
                             :entity-type :campaign
                             :entity-id id
                             :identity identity
                             :entity campaign)))
    (GET "/:id" []
      :return (s/maybe StoredCampaign)
      :summary "Gets a Campaign by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-campaign
      (if-let [d (read-store :campaign read-campaign id)]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a Campaign"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-campaign
      :identity identity
      (if (flows/delete-flow :get-fn #(read-store :campaign read-campaign %)
                             :delete-fn #(write-store :campaign delete-campaign %)
                             :entity-type :campaign
                             :entity-id id
                             :identity identity)
        (no-content)
        (not-found)))))
