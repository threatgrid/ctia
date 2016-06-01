(ns ctia.http.routes.campaign
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.flows.crud :as flows]
            [ctia.schemas.campaign :refer [NewCampaign StoredCampaign realize-campaign]]
            [ctia.store :refer :all]))

(defroutes campaign-routes
  (context "/campaign" []
    :tags ["Campaign"]
    (POST "/" []
      :return StoredCampaign
      :body [campaign NewCampaign {:description "a new campaign"}]
      :summary "Adds a new Campaign"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-campaign
      :login login
      (ok (flows/create-flow :realize-fn realize-campaign
                             :store-fn #(write-store :campaign
                                                     (fn [s] (create-campaign s %)))
                             :entity-type :campaign
                             :login login
                             :entity campaign)))
    (PUT "/:id" []
      :return StoredCampaign
      :body [campaign NewCampaign {:description "an updated campaign"}]
      :summary "Updates a Campaign"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-campaign
      :login login
      (ok (flows/update-flow :get-fn #(read-store :campaign
                                                  (fn [s] (read-campaign s %)))
                             :realize-fn realize-campaign
                             :update-fn #(write-store :campaign
                                                      (fn [s] (update-campaign s (:id %) %)))
                             :entity-type :campaign
                             :id id
                             :login login
                             :entity campaign)))
    (GET "/:id" []
      :return (s/maybe StoredCampaign)
      :summary "Gets a Campaign by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-campaign
      (if-let [d (read-store :campaign (fn [s] (read-campaign s id)))]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a Campaign"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-campaign
      :login login
      (if (flows/delete-flow :get-fn #(read-store :campaign
                                                  (fn [s] (read-campaign s %)))
                             :delete-fn #(write-store :campaign
                                                      (fn [s] (delete-campaign s %)))
                             :entity-type :campaign
                             :id id
                             :login login)
        (no-content)
        (not-found)))))
