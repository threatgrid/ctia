(ns ctia.http.routes.sighting
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :refer [realize-sighting]]
   [ctia.domain.entities.sighting :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewSighting StoredSighting]]
   [ctia.http.routes.common
    :refer [created paginated-ok PagingParams SightingSearchParams]]
   [ctia.http.middleware
    [cache-control :refer [wrap-cache-control]]
    [un-store :refer [wrap-un-store]]]
   [ring.util.http-response :refer [ok no-content not-found unprocessable-entity]]
   [schema.core :as s]
   [schema-tools.core :as st]))

(s/defschema SightingByExternalIdQueryParams
  PagingParams)

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
                 :middleware [wrap-un-store]
                 (created
                  (first
                   (flows/create-flow
                    :realize-fn realize-sighting
                    :store-fn #(write-store :sighting create-sightings %)
                    :long-id-fn with-long-id
                    :entity-type :sighting
                    :identity identity
                    :entities [sighting]))))

           (PUT "/:id" []
                :return StoredSighting
                :body [sighting NewSighting {:description "An updated Sighting"}]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "Updates a Sighting"
                :path-params [id :- s/Str]
                :capabilities :create-sighting
                :identity identity
                :middleware [wrap-un-store]
                (ok
                 (flows/update-flow
                  :get-fn #(read-store :sighting read-sighting %)
                  :realize-fn realize-sighting
                  :update-fn #(write-store :sighting update-sighting (:id %) %)
                  :long-id-fn with-long-id
                  :entity-type :sighting
                  :entity-id id
                  :identity identity
                  :entity sighting)))

           (GET "/external_id/:external_id" []
                :return (s/maybe [StoredSighting])
                :query [q SightingByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "List sightings by external id"
                :capabilities #{:read-sighting :external-id}
                :middleware [wrap-un-store wrap-cache-control]
                (paginated-ok
                 (page-with-long-id
                  (read-store :sighting list-sightings {:external_ids external_id} q))))

           (GET "/search" []
                :return (s/maybe [StoredSighting])
                :summary "Search for Sightings using a Lucene/ES query string"
                :query [params SightingSearchParams]
                :capabilities #{:read-sighting :search-sighting}
                :header-params [api_key :- (s/maybe s/Str)]
                :middleware [wrap-un-store wrap-cache-control]
                (paginated-ok
                 (page-with-long-id
                  (query-string-search-store
                   :sighting
                   query-string-search
                   (:query params)
                   (dissoc params :query :sort_by :sort_order :offset :limit)
                   (select-keys params [:sort_by :sort_order :offset :limit])))))

           (GET "/:id" []
                :return (s/maybe StoredSighting)
                :summary "Gets a Sighting by ID"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :read-sighting
                :middleware [wrap-un-store wrap-cache-control]
                (if-let [d (read-store :sighting read-sighting id)]
                  (ok (with-long-id d))
                  (not-found)))

           (DELETE "/:id" []
                   :path-params [id :- s/Str]
                   :summary "Deletes a Sighting"
                   :header-params [api_key :- (s/maybe s/Str)]
                   :capabilities :delete-sighting
                   (if (write-store :sighting delete-sighting id)
                     (no-content)
                     (not-found)))))
