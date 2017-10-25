(ns ctia.http.routes.investigation
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.investigation
    :refer [with-long-id
            page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created
            paginated-ok
            PagingParams
            ActorSearchParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [Investigation NewInvestigation]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))


(s/defschema InvestigationsByExternalIdQueryParams
  PagingParams)

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

           (PUT "/:id" []
                :return Investigation
                :body [investigation NewInvestigation
                       {:description "an updated Investigation"}]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "Updates an Investigation"
                :path-params [id :- s/Str]
                :capabilities :create-investigation
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow
                     :get-fn #(read-store :investigation
                                          read-investigation
                                          %
                                          identity-map)
                     :realize-fn ent/realize-investigation
                     :update-fn #(write-store :investigation
                                              update-investigation
                                              (:id %)
                                              %
                                              identity-map)
                     :long-id-fn with-long-id
                     :entity-type :investigation
                     :entity-id id
                     :identity identity
                     :entity investigation
                     :spec :new-investigation/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return (s/maybe [Investigation])
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

           (GET "/:id" []
                :return (s/maybe Investigation)
                :summary "Gets an Investigation by ID"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-investigation
                :identity identity
                :identity-map identity-map
                (if-let [investigation (read-store :investigation
                                                   read-investigation
                                                   id
                                                   identity-map)]
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
                                             identity-map)
                        :delete-fn #(write-store :investigation
                                                 delete-investigation
                                                 %
                                                 identity-map)
                        :entity-type :investigation
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
