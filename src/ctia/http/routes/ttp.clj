(ns ctia.http.routes.ttp
  (:require
    [compojure.api.sweet :refer :all]
    [ctia.domain.entities :refer [realize-ttp]]
    [ctia.flows.crud :as flows]
    [ctia.store :refer :all]
    [ctim.schemas.ttp :refer [NewTTP StoredTTP]]
    [ring.util.http-response :refer :all]
    [schema.core :as s]))

(defroutes ttp-routes
  (context "/ttp" []
    :tags ["TTP"]
    (POST "/" []
      :return StoredTTP
      :body [ttp NewTTP {:description "a new TTP"}]
      :summary "Adds a new TTP"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-ttp
      :identity identity
      (created (flows/create-flow :realize-fn realize-ttp
                                  :store-fn #(write-store :ttp create-ttp %)
                                  :entity-type :ttp
                                  :identity identity
                                  :entity ttp)))
    (PUT "/:id" []
      :return StoredTTP
      :body [ttp NewTTP {:description "an updated TTP"}]
      :summary "Updates a TTP"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-ttp
      :identity identity
      (ok (flows/update-flow :get-fn #(read-store :ttp
                                                  (fn [s] (read-ttp s %)))
                             :realize-fn realize-ttp
                             :update-fn #(write-store :ttp update-ttp (:id %) %)
                             :entity-type :ttp
                             :entity-id id
                             :identity identity
                             :entity ttp)))
    (GET "/:id" []
      :return (s/maybe StoredTTP)
      :summary "Gets a TTP by ID"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-ttp
      :path-params [id :- s/Str]
      (if-let [d (read-store :ttp
                             (fn [s] (read-ttp s id)))]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a TTP"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-ttp
      :identity identity
      (if (flows/delete-flow :get-fn #(read-store :ttp read-ttp %)
                             :delete-fn #(write-store :ttp delete-ttp %)
                             :entity-type :ttp
                             :entity-id id
                             :identity identity)
        (no-content)
        (not-found)))))
