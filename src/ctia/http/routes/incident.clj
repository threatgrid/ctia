(ns ctia.http.routes.incident
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities :as ent]
            [ctia.domain.entities.incident :refer [with-long-id page-with-long-id]]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.schemas.core :refer [NewIncident Incident]]
            [ctia.http.routes.common :refer [created IncidentSearchParams
                                             PagingParams paginated-ok]]
            [ring.util.http-response :refer [ok no-content not-found]]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema IncidentByExternalIdQueryParams
  PagingParams)

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
                 (-> (flows/create-flow
                      :realize-fn ent/realize-incident
                      :store-fn #(write-store :incident create-incidents %)
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
                (-> (flows/update-flow
                     :get-fn #(read-store :incident read-incident %)
                     :realize-fn ent/realize-incident
                     :update-fn #(write-store :incident update-incident (:id %) %)
                     :long-id-fn with-long-id
                     :entity-type :incident
                     :entity-id id
                     :identity identity
                     :entity incident
                     :spec :new-incident/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return [(s/maybe Incident)]
                :query [q IncidentByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List Incidents by external id"
                :capabilities #{:read-incident :external-id}
                (-> (read-store :incident list-incidents
                                {:external_ids external_id} q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return (s/maybe [Incident])
                :summary "Search for an Incident using a Lucene/ES query string"
                :query [params IncidentSearchParams]
                :capabilities #{:read-incident :search-incident}
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                (-> (query-string-search-store
                     :incident
                     query-string-search
                     (:query params)
                     (dissoc params :query :sort_by :sort_order :offset :limit)
                     (select-keys params [:sort_by :sort_order :offset :limit]))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe Incident)
                :summary "Gets an Incident by ID"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-incident
                (if-let [incident (read-store :incident read-incident id)]
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
                   (if (flows/delete-flow
                        :get-fn #(read-store :incident read-incident %)
                        :delete-fn #(write-store :incident delete-incident %)
                        :entity-type :incident
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
