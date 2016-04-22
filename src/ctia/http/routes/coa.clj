(ns ctia.http.routes.coa
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.schemas.coa :refer [NewCOA
                                      StoredCOA
                                      realize-coa]]))

(defroutes coa-routes
  (context "/coa" []
    :tags ["COA"]
    (POST "/" []
      :return StoredCOA
      :body [coa NewCOA {:description "a new COA"}]
      :summary "Adds a new COA"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:create-coa :admin}
      :login login
      (ok (flows/create-flow :realize-fn realize-coa
                             :store-fn #(create-coa @coa-store %)
                             :object-type :coa
                             :login login
                             :object coa)))
    (PUT "/:id" []
      :return StoredCOA
      :body [coa NewCOA {:description "an updated COA"}]
      :summary "Updates a COA"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:create-coa :admin}
      :login login
      (ok (flows/update-flow :get-fn #(read-coa @coa-store %)
                             :realize-fn realize-coa
                             :update-fn #(update-coa @coa-store (:id %) %)
                             :object-type :coa
                             :id id
                             :login login
                             :object coa)))
    (GET "/:id" []
      :return (s/maybe StoredCOA)
      :summary "Gets a COA by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:read-coa :admin}
      (if-let [d (read-coa @coa-store id)]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a COA"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:delete-coa :admin}
      (if (flows/delete-flow :get-fn #(read-coa @coa-store %)
                             :delete-fn #(delete-coa @coa-store %)
                             :object-type :coa
                             :id id)
        (no-content)
        (not-found)))))
