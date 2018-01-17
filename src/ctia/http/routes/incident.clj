(ns ctia.http.routes.incident
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities :as ent]
            [ctia.domain.entities.incident
             :refer [with-long-id page-with-long-id]]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.schemas.core
             :refer [NewIncident
                     Incident
                     PartialIncident
                     PartialIncidentList]]
            [ctia.http.routes.common :refer [created
                                             search-options
                                             filter-map-search-options
                                             paginated-ok
                                             IncidentByExternalIdQueryParams
                                             IncidentSearchParams
                                             IncidentGetParams
                                             PagingParams]]
            [ring.util.http-response :refer [ok no-content not-found]]
            [schema.core :as s]
            [schema-tools.core :as st]))

(defroutes incident-routes
  (context "/incident" []
           :tags ["Incident"]
           (POST "/" []
                 :return Incident
                 :body [incident NewIncident {:description "a new incident"}]
                 :summary "Adds a new Incident"
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :capabilities :create-incident
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :realize-fn ent/realize-incident
                      :store-fn #(write-store :incident
                                              create-incidents
                                              %
                                              identity-map)
                      :long-id-fn with-long-id
                      :entity-type :incident
                      :identity identity
                      :entities [incident]
                      :spec :new-incident/map)
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return Incident
                :body [incident NewIncident {:description "an updated incident"}]
                :summary "Updates an Incident"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :create-incident
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow
                     :get-fn #(read-store :incident
                                          read-incident
                                          %
                                          identity-map
                                          {})
                     :realize-fn ent/realize-incident
                     :update-fn #(write-store :incident
                                              update-incident
                                              (:id %)
                                              %
                                              identity-map)
                     :long-id-fn with-long-id
                     :entity-type :incident
                     :entity-id id
                     :identity identity
                     :entity incident
                     :spec :new-incident/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return PartialIncidentList
                :query [q IncidentByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List Incidents by external id"
                :capabilities #{:read-incident :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :incident list-incidents
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return PartialIncidentList
                :summary "Search for an Incident using a Lucene/ES query string"
                :query [params IncidentSearchParams]
                :capabilities #{:read-incident :search-incident}
                :identity identity
                :identity-map identity-map
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                (-> (query-string-search-store
                     :incident
                     query-string-search
                     (:query params)
                     (apply dissoc params filter-map-search-options)
                     identity-map
                     (select-keys params search-options))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe PartialIncident)
                :summary "Gets an Incident by ID"
                :path-params [id :- s/Str]
                :query [params IncidentGetParams]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-incident
                :identity identity
                :identity-map identity-map
                (if-let [incident (read-store :incident
                                              read-incident
                                              id
                                              identity-map
                                              params)]
                  (-> incident
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes an Incident"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-incident
                   :identity identity
                   :identity-map identity-map
                   (if (flows/delete-flow
                        :get-fn #(read-store :incident
                                             read-incident
                                             %
                                             identity-map
                                             {})
                        :delete-fn #(write-store :incident
                                                 delete-incident
                                                 %
                                                 identity-map)
                        :entity-type :incident
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
