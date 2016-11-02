(ns ctia.http.routes.sighting
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :refer [realize-sighting check-new-sighting]]
   [ctia.domain.entities.sighting :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as f]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewSighting StoredSighting]]
   [ctia.http.routes.common :refer [created paginated-ok PagingParams]]
   [ring.util.http-response :refer [ok no-content not-found unprocessable-entity]]
   [schema.core :as s]
   [schema-tools.core :as st]))

(s/defschema SightingByExternalIdQueryParams
  (st/merge
   PagingParams
   {:external_id s/Str}))

(defroutes sighting-routes
  (context "/sighting" []
    :tags ["Sighting"]
    (POST "/" []
      :return StoredSighting
      :body [sighting NewSighting {:description "A new Sighting"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Adds a new Sighting"
      :capabilities :create-sighting
      :identity identity
      (if (check-new-sighting sighting)
        (created
         (with-long-id
           (f/pop-result
            (f/create-flow :realize-fn realize-sighting
                           :store-fn #(write-store :sighting create-sighting %)
                           :entity-type :sighting
                           :identity identity
                           :entity sighting))))
        (unprocessable-entity)))

    (PUT "/:id" []
      :return StoredSighting
      :body [sighting NewSighting {:description "An updated Sighting"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Updates a Sighting"
      :path-params [id :- s/Str]
      :capabilities :create-sighting
      :identity identity
      (if (check-new-sighting sighting)
        (ok
         (with-long-id
           (f/pop-result
            (f/update-flow :get-fn #(read-store :sighting read-sighting %)
                           :realize-fn realize-sighting
                           :update-fn #(write-store :sighting update-sighting id %)
                           :entity-type :sighting
                           :entity-id id
                           :identity identity
                           :entity sighting))))
        (unprocessable-entity)))

    (GET "/external_id" []
      :return [(s/maybe StoredSighting)]
      :query [q SightingByExternalIdQueryParams]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "List sightings by external id"
      :capabilities #{:read-sighting :external-id}
      (paginated-ok
       (page-with-long-id
        (read-store :sighting list-sightings
                    {:external_ids (:external_id q)} q))))

    (GET "/:id" []
      :return (s/maybe StoredSighting)
      :summary "Gets a Sighting by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-sighting
      (if-let [d (read-store :sighting read-sighting id)]
        (ok (with-long-id d))
        (not-found)))

    (DELETE "/:id" []
      :path-params [id :- s/Str]
      :summary "Deletes a Sighting"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-sighting
      :identity identity
      (if (f/pop-result
           (f/delete-flow :get-fn #(read-store :sighting read-sighting %)
                          :delete-fn #(write-store :sighting delete-sighting %)
                          :entity-type :sighting
                          :entity-id id
                          :identity identity))
        (no-content)
        (not-found)))))
