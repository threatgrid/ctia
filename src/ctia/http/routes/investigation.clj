(ns ctia.http.routes.investigation
  (:require
   [clojure.tools.logging :as log]
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.investigation
    :refer [with-long-id
            page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created
            paginated-ok
            filter-map-search-options
            search-options
            PagingParams
            InvestigationSearchParams
            InvestigationGetParams
            InvestigationsByExternalIdQueryParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core
    :refer [Investigation NewInvestigation PartialInvestigation PartialInvestigationList]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(defroutes investigation-routes
  (context "/investigation" []
           :tags ["Investigation"]
           (POST "/" []
                 :return Investigation
                 :body [investigation NewInvestigation
                        {:description "a new Investigation"}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "Adds a new Investigation"
                 :capabilities :create-investigation
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :realize-fn ent/realize-investigation
                      :store-fn #(write-store :investigation
                                              create-investigations
                                              %
                                              identity-map)
                      :long-id-fn with-long-id
                      :entity-type :investigation
                      :identity identity
                      :entities [investigation]
                      :spec :new-investigation/map)
                     first
                     ent/un-store
                     created))

           (GET "/external_id/:external_id" []
                :return PartialInvestigationList
                :query [q InvestigationsByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "Get Investigations by external IDs"
                :capabilities #{:read-investigation :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :investigation
                                list-investigations
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return PartialInvestigationList
                :summary "Search for an Investigation using a Lucene/ES query string"
                :query [params InvestigationSearchParams]
                :capabilities #{:read-investigation :search-investigation}
                :identity identity
                :identity-map identity-map
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                (-> (query-string-search-store
                     :investigation
                     query-string-search
                     (:query params)
                     (apply dissoc params filter-map-search-options)
                     identity-map
                     (select-keys params search-options))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe PartialInvestigation)
                :summary "Gets an Investigation by ID"
                :path-params [id :- s/Str]
                :query [params InvestigationGetParams]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-investigation
                :identity identity
                :identity-map identity-map
                (if-let [investigation (read-store :investigation
                                                   read-investigation
                                                   id
                                                   identity-map
                                                   params)]
                  (-> investigation
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes an Investigation"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-investigation
                   :identity identity
                   :identity-map identity-map
                   (if (flows/delete-flow
                        :get-fn #(read-store :investigation
                                             read-investigation
                                             %
                                             identity-map
                                             {})
                        :delete-fn #(write-store :investigation
                                                 delete-investigation
                                                 %
                                                 identity-map)
                        :entity-type :investigation
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
