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
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Adds a new Actor"
      :capabilities :create-actor
      :identity identity
      (ok (flows/create-flow :entity-type :actor
                             :realize-fn realize-actor
                             :store-fn #(create-actor @actor-store %)
                             :identity identity
                             :entity actor)))
    (PUT "/:id" []
      :return StoredActor
      :body [actor NewActor {:description "an updated Actor"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Updates an Actor"
      :path-params [id :- s/Str]
      :capabilities :create-actor
      :identity identity
      (ok (flows/update-flow :entity-type :actor
                             :get-fn #(read-actor @actor-store %)
                             :realize-fn realize-actor
                             :update-fn #(update-actor @actor-store (:id %) %)
                             :entity-id id
                             :identity identity
                             :entity actor)))
    (GET "/:id" []
      :return (s/maybe StoredActor)
      :summary "Gets an Actor by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-actor
      (if-let [d (read-actor @actor-store id)]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes an Actor"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-actor
      :identity identity
      (if (flows/delete-flow :entity-type :actor
                             :get-fn #(read-actor @actor-store %)
                             :delete-fn #(delete-actor @actor-store %)
                             :entity-id id
                             :identity identity)
        (no-content)
        (not-found)))))
