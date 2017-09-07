(ns ctia.http.routes.sighting
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.sighting :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewSighting Sighting]]
   [ctia.http.routes.common
    :refer [created paginated-ok PagingParams SightingSearchParams]]
   [ring.util.http-response :refer [ok no-content not-found unprocessable-entity]]
   [schema.core :as s]
   [schema-tools.core :as st]))

(s/defschema SightingByExternalIdQueryParams
  PagingParams)

(defroutes sighting-routes
  (context "/sighting" []
           :tags ["Sighting"]
           (POST "/" []
                 :return Sighting
                 :body [sighting NewSighting {:description "A new Sighting"}]
                 :header-params [api_key :- (s/maybe s/Str)]
                 :summary "Adds a new Sighting"
                 :capabilities :create-sighting
                 :identity identity
                 (-> (flows/create-flow
                      :realize-fn ent/realize-sighting
                      :store-fn #(write-store :sighting create-sightings %)
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
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "Updates a Sighting"
                :path-params [id :- s/Str]
                :capabilities :create-sighting
                :identity identity
                (-> (flows/update-flow
                     :get-fn #(read-store :sighting read-sighting %)
                     :realize-fn ent/realize-sighting
                     :update-fn #(write-store :sighting update-sighting (:id %) %)
                     :long-id-fn with-long-id
                     :entity-type :sighting
                     :entity-id id
                     :identity identity
                     :entity sighting
                     :spec :new-sighting/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return (s/maybe [Sighting])
                :query [q SightingByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "List sightings by external id"
                :capabilities #{:read-sighting :external-id}
                (-> (read-store :sighting list-sightings {:external_ids external_id} q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return (s/maybe [Sighting])
                :summary "Search for Sightings using a Lucene/ES query string"
                :query [params SightingSearchParams]
                :capabilities #{:read-sighting :search-sighting}
                :header-params [api_key :- (s/maybe s/Str)]
                (-> (query-string-search-store
                     :sighting
                     query-string-search
                     (:query params)
                     (dissoc params :query :sort_by :sort_order :offset :limit)
                     (select-keys params [:sort_by :sort_order :offset :limit]))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe Sighting)
                :summary "Gets a Sighting by ID"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :read-sighting
                (if-let [sighting (read-store :sighting read-sighting id)]
                  (-> sighting
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :path-params [id :- s/Str]
                   :summary "Deletes a Sighting"
                   :header-params [api_key :- (s/maybe s/Str)]
                   :capabilities :delete-sighting
                   (if (write-store :sighting delete-sighting id)
                     (no-content)
                     (not-found)))))
