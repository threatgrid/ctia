(ns ctia.http.routes.actor
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.flows.crud :as flows]
            [ctia.schemas.actor :refer [NewActor StoredActor realize-actor]]
            [ctia.store :refer :all]))

(defroutes actor-routes
  (context "/actor" []
    :tags ["Actor"]
    (POST "/" []
      :return StoredActor
      :body [actor NewActor {:description "a new Actor"}]
      :header-params [api_key :- s/Str]
      :summary "Adds a new Actor"
      :capabilities #{:create-actor :admin}
      :login login
      (ok (flows/create-flow :realize-fn realize-actor
                             :store-fn #(create-actor @actor-store %)
                             :object-type :actor
                             :login login
                             :object actor)))
    (PUT "/:id" []
      :return StoredActor
      :body [actor NewActor {:description "an updated Actor"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Updates an Actor"
      :path-params [id :- s/Str]
      :capabilities #{:create-actor :admin}
      :login login
      (ok (flows/update-flow :get-fn #(read-actor @actor-store %)
                             :realize-fn realize-actor
                             :update-fn #(update-actor @actor-store (:id %) %)
                             :object-type :actor
                             :id id
                             :login login
                             :object actor)))
    (GET "/:id" []
      :return (s/maybe StoredActor)
      :summary "Gets an Actor by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:read-actor :admin}
      (if-let [d (read-actor @actor-store id)]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes an Actor"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:delete-actor :admin}
      (if (flows/delete-flow :get-fn #(read-actor @actor-store %)
                             :delete-fn #(delete-actor @actor-store %)
                             :object-type :actor
                             :id id)
        (no-content)
        (not-found)))))
