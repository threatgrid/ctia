(ns ctia.http.routes.coa
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities :refer [realize-coa]]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctim.schemas.coa :refer [NewCOA StoredCOA]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defroutes coa-routes
  (context "/coa" []
    :tags ["COA"]
    (POST "/" []
      :return StoredCOA
      :body [coa NewCOA {:description "a new COA"}]
      :summary "Adds a new COA"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-coa
      :identity identity
      (created (flows/create-flow :realize-fn realize-coa
                                  :store-fn #(write-store :coa create-coa %)
                                  :entity-type :coa
                                  :identity identity
                                  :entity coa)))
    (PUT "/:id" []
      :return StoredCOA
      :body [coa NewCOA {:description "an updated COA"}]
      :summary "Updates a COA"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-coa
      :identity identity
      (ok (flows/update-flow :get-fn #(read-store :coa read-coa %)
                             :realize-fn realize-coa
                             :update-fn #(write-store :coa update-coa (:id %) %)
                             :entity-type :coa
                             :entity-id id
                             :identity identity
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
      :identity identity
      (if (flows/delete-flow :get-fn #(read-store :coa read-coa %)
                             :delete-fn #(write-store :coa delete-coa %)
                             :entity-type :coa
                             :entity-id id
                             :identity identity)
        (no-content)
        (not-found)))))
