(ns ctia.http.routes.actor
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :refer [realize-actor]]
   [ctia.domain.entities.actor :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as f]
   [ctia.http.routes.common :refer [created paginated-ok PagingParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewActor StoredActor]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema ActorByExternalIdQueryParams
  (st/merge
   PagingParams
   {:external_id s/Str}))

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
      (created
       (with-long-id
         (f/pop-result
          (f/create-flow :entity-type :actor
                         :realize-fn realize-actor
                         :store-fn #(write-store :actor create-actor %)
                         :identity identity
                         :entity actor)))))
    (PUT "/:id" []
      :return StoredActor
      :body [actor NewActor {:description "an updated Actor"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Updates an Actor"
      :path-params [id :- s/Str]
      :capabilities :create-actor
      :identity identity
      (ok
       (with-long-id
         (f/pop-result
          (f/update-flow :get-fn #(read-store :actor read-actor %)
                         :realize-fn realize-actor
                         :update-fn #(write-store :actor update-actor id %)
                         :entity-type :actor
                         :entity-id id
                         :identity identity
                         :entity actor)))))

    (GET "/external_id" []
      :return [(s/maybe StoredActor)]
      :query [q ActorByExternalIdQueryParams]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "List actors by external id"
      :capabilities #{:read-actor :external-id}
      (paginated-ok
       (page-with-long-id
        (read-store :actor list-actors
                    {:external_ids (:external_id q)} q))))

    (GET "/:id" []
      :return (s/maybe StoredActor)
      :summary "Gets an Actor by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-actor
      (if-let [d (read-store :actor read-actor id)]
        (ok (with-long-id d))
        (not-found)))

    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes an Actor"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-actor
      :identity identity
      (if (f/pop-result
           (f/delete-flow :get-fn #(read-store :actor read-actor %)
                          :delete-fn #(write-store :actor delete-actor %)
                          :entity-type :actor
                          :entity-id id
                          :identity identity))
        (no-content)
        (not-found)))))
