(ns ctia.http.routes.sighting
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.sighting :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.store :refer :all]
   [ctia.schemas.core
    :refer [NewSighting Sighting PartialSighting PartialSightingList]]
   [ctia.http.routes.common
    :refer [created
            paginated-ok
            search-options
            filter-map-search-options
            PagingParams
            SightingGetParams
            SightingSearchParams
            SightingByExternalIdQueryParams]]
   [ring.util.http-response :refer [ok no-content not-found unprocessable-entity]]
   [schema.core :as s]
   [schema-tools.core :as st]))

(defroutes sighting-routes
  (context "/sighting" []
           :tags ["Sighting"]
           (POST "/" []
                 :return Sighting
                 :body [sighting NewSighting {:description "A new Sighting"}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "Adds a new Sighting"
                 :capabilities :create-sighting
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :realize-fn ent/realize-sighting
                      :store-fn #(write-store :sighting
                                              create-sightings
                                              %
                                              identity-map)
                      :long-id-fn with-long-id
                      :entity-type :sighting
                      :identity identity
                      :entities [sighting]
                      :spec :new-sighting/map)
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return Sighting
                :body [sighting NewSighting {:description "An updated Sighting"}]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "Updates a Sighting"
                :path-params [id :- s/Str]
                :capabilities :create-sighting
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow
                     :get-fn #(read-store :sighting
                                          read-sighting
                                          %
                                          identity-map
                                          {})
                     :realize-fn ent/realize-sighting
                     :update-fn #(write-store :sighting
                                              update-sighting
                                              (:id %)
                                              %
                                              identity-map)
                     :long-id-fn with-long-id
                     :entity-type :sighting
                     :entity-id id
                     :identity identity
                     :entity sighting
                     :spec :new-sighting/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return PartialSightingList
                :query [q SightingByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List sightings by external id"
                :capabilities #{:read-sighting :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :sighting
                                list-sightings
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return PartialSightingList
                :summary "Search for Sightings using a Lucene/ES query string"
                :query [params SightingSearchParams]
                :capabilities #{:read-sighting :search-sighting}
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :identity identity
                :identity-map identity-map
                (-> (query-string-search-store
                     :sighting
                     query-string-search
                     (:query params)
                     (apply dissoc params filter-map-search-options)
                     identity-map
                     (select-keys params search-options))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe PartialSighting)
                :summary "Gets a Sighting by ID"
                :path-params [id :- s/Str]
                :query [params SightingGetParams]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-sighting
                :identity identity
                :identity-map identity-map
                (if-let [sighting (read-store :sighting
                                              read-sighting
                                              id
                                              identity-map
                                              params)]
                  (-> sighting
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :path-params [id :- s/Str]
                   :summary "Deletes a Sighting"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-sighting
                   :identity identity
                   :identity-map identity-map
                   (if (write-store :sighting
                                    delete-sighting
                                    id
                                    identity-map)
                     (no-content)
                     (not-found)))))
