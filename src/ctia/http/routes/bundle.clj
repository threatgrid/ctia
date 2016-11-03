(ns ctia.http.routes.bundle
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities :refer [realize-bundle]]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.http.routes.common :refer [created paginated-ok PagingParams]]
            [ctia.schemas.core :refer [NewBundle StoredBundle]]
            [ring.util.http-response :refer [ok no-content not-found]]
            [schema.core :as s]
            [schema-tools.core :as st]))

(defroutes bundle-routes
  (context "/bundle" []
    :tags ["Bundle"]
    (POST "/" []
      :return StoredBundle
      :body [bundle NewBundle {:description "a new Bundle"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Adds a new Bundle"
      :capabilities :create-bundle
      :identity identity
      (created
       (first
        (flows/create-flow :entity-type :bundle
                           :realize-fn realize-bundle
                           :store-fn #(write-store :bundle create-bundles %)
                           :entity-type :bundle
                           :identity identity
                           :entities [bundle]))))
    (GET "/:id" []
      :return (s/maybe StoredBundle)
      :summary "Gets a Bundle by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-bundle
      (if-let [d (read-store :bundle read-bundle id)]
        (ok d)
        (not-found)))

    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a Bundle"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-bundle
      :identity identity
      (if (flows/delete-flow :get-fn #(read-store :bundle read-bundle %)
                             :delete-fn #(write-store :bundle delete-bundle %)
                             :entity-type :bundle
                             :entity-id id
                             :identity identity)
        (no-content)
        (not-found)))))
