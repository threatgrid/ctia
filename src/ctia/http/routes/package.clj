(ns ctia.http.routes.package
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities :refer [realize-package]]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.http.routes.common :refer [paginated-ok PagingParams]]
            [ctim.schemas.package :refer [NewPackage StoredPackage]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]))

(defroutes package-routes
  (context "/package" []
    :tags ["Package"]
    (POST "/" []
      :return StoredPackage
      :body [package NewPackage {:description "a new Package"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Adds a new Package"
      :capabilities :create-package
      :identity identity
      (created (flows/create-flow :entity-type :package
                                  :realize-fn realize-package
                                  :store-fn #(write-store :package create-package %)
                                  :entity-type :package
                                  :identity identity
                                  :entity package)))
    (GET "/:id" []
      :return (s/maybe StoredPackage)
      :summary "Gets a Package by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-package
      (if-let [d (read-store :package read-package id)]
        (ok d)
        (not-found)))

    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a Package"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-package
      :identity identity
      (if (flows/delete-flow :get-fn #(read-store :package read-package %)
                             :delete-fn #(write-store :package delete-package %)
                             :entity-type :package
                             :entity-id id
                             :identity identity)
        (no-content)
        (not-found)))))
