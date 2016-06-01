(ns ctia.http.routes.ttp
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.schemas.ttp :refer [NewTTP StoredTTP realize-ttp]]))

(defroutes ttp-routes
  (context "/ttp" []
    :tags ["TTP"]
    (POST "/" []
      :return StoredTTP
      :body [ttp NewTTP {:description "a new TTP"}]
      :summary "Adds a new TTP"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-ttp
      :login login
      (ok (flows/create-flow :realize-fn realize-ttp
                             :store-fn #(read-store :ttp
                                                    (fn [s] (create-ttp s %)))
                             :entity-type :ttp
                             :login login
                             :entity ttp)))
    (PUT "/:id" []
      :return StoredTTP
      :body [ttp NewTTP {:description "an updated TTP"}]
      :summary "Updates a TTP"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-ttp
      :login login
      (ok (flows/update-flow :get-fn #(read-store :ttp
                                                  (fn [s] (read-ttp s %)))
                             :realize-fn realize-ttp
                             :update-fn #(write-store :ttp
                                                      (fn [s] (update-ttp s (:id %) %)))
                             :entity-type :ttp
                             :id id
                             :login login
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
      :login login
      (if (flows/delete-flow :get-fn #(read-store :ttp
                                                  (fn [s] (read-ttp s %)))
                             :delete-fn #(write-store :ttp (fn [s] (delete-ttp s %)))
                             :entity-type :ttp
                             :id id
                             :login login)
        (no-content)
        (not-found)))))
