(ns ctia.http.routes.coa
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.schemas.coa :refer [NewCOA StoredCOA realize-coa]]))

(defroutes coa-routes
  (context "/coa" []
    :tags ["COA"]
    (POST "/" []
      :return StoredCOA
      :body [coa NewCOA {:description "a new COA"}]
      :summary "Adds a new COA"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-coa
      :login login
      (ok (flows/create-flow :realize-fn realize-coa
                             :store-fn #(write-store :coa
                                                     (fn [s] (create-coa s %)))
                             :entity-type :coa
                             :login login
                             :entity coa)))
    (PUT "/:id" []
      :return StoredCOA
      :body [coa NewCOA {:description "an updated COA"}]
      :summary "Updates a COA"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-coa
      :login login
      (ok (flows/update-flow :get-fn #(read-store :coa
                                                  (fn [s] (read-coa s %)))
                             :realize-fn realize-coa
                             :update-fn #(write-store :coa
                                                      (fn [s] (update-coa s (:id %) %)))
                             :entity-type :coa
                             :id id
                             :login login
                             :entity coa)))
    (GET "/:id" []
      :return (s/maybe StoredCOA)
      :summary "Gets a COA by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-coa
      (if-let [d (read-store :coa (fn [s] (read-coa s id)))]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a COA"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-coa
      :login login
      (if (flows/delete-flow :get-fn #(read-store :coa
                                                  (fn [s] (read-coa s %)))
                             :delete-fn #(write-store :coa
                                                      (fn [s] (delete-coa s %)))
                             :entity-type :coa
                             :id id
                             :login login)
        (no-content)
        (not-found)))))
