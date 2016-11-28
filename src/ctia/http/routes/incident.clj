(ns ctia.http.routes.incident
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities :refer [realize-incident]]
            [ctia.domain.entities.incident :refer [with-long-id page-with-long-id]]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.schemas.core :refer [NewIncident StoredIncident]]
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
                 :return StoredIncident
                 :body [incident NewIncident {:description "a new incident"}]
                 :summary "Adds a new Incident"
                 :header-params [api_key :- (s/maybe s/Str)]
                 :capabilities :create-incident
                 :identity identity
                 (created
                  (first
                   (flows/create-flow
                    :realize-fn realize-incident
                    :store-fn #(write-store :incident create-incidents %)
                    :long-id-fn with-long-id
                    :entity-type :incident
                    :identity identity
                    :entities [incident]))))

           (PUT "/:id" []
                :return StoredIncident
                :body [incident NewIncident {:description "an updated incident"}]
                :summary "Updates an Incident"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :create-incident
                :identity identity
                (ok (flows/update-flow
                     :get-fn #(read-store :incident read-incident %)
                     :realize-fn realize-incident
                     :update-fn #(write-store :incident update-incident (:id %) %)
                     :long-id-fn with-long-id
                     :entity-type :incident
                     :entity-id id
                     :identity identity
                     :entity incident)))

           (GET "/external_id/:external_id" []
                :return [(s/maybe StoredIncident)]
                :query [q IncidentByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "List Incidents by external id"
                :capabilities #{:read-incident :external-id}
                (paginated-ok
                 (page-with-long-id
                  (read-store :incident list-incidents
                              {:external_ids external_id} q))))

           (GET "/search" []
                :return (s/maybe [StoredIncident])
                :summary "Search for an Incident using a Lucene/ES query string"
                :query [params IncidentSearchParams]
                :capabilities #{:read-incident :search-incident}
                :header-params [api_key :- (s/maybe s/Str)]
                (paginated-ok
                 (page-with-long-id
                  (query-string-search-store
                   :incident
                   query-string-search
                   (:query params)
                   (dissoc params :query :sort_by :sort_order :offset :limit)
                   (select-keys params [:sort_by :sort_order :offset :limit])))))

           (GET "/:id" []
                :return (s/maybe StoredIncident)
                :summary "Gets an Incident by ID"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :read-incident
                (if-let [d (read-store :incident read-incident id)]
                  (ok (with-long-id d))
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes an Incident"
                   :header-params [api_key :- (s/maybe s/Str)]
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
