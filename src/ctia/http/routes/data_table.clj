(ns ctia.http.routes.data-table
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.data-table :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created
            paginated-ok
            PagingParams
            DataTableGetParams
            DataTableByExternalIdQueryParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core
    :refer [NewDataTable DataTable PartialDataTable PartialDataTableList]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(defroutes data-table-routes
  (context "/data-table" []
           :tags ["DataTable"]
           (POST "/" []
                 :return DataTable
                 :body [data-table NewDataTable {:description "a new Data Table"}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "Adds a new Data Table"
                 :capabilities :create-data-table
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :entity-type :data-table
                      :realize-fn ent/realize-data-table
                      :store-fn #(write-store :data-table
                                              create-data-tables
                                              %
                                              identity-map
                                              {})
                      :long-id-fn with-long-id
                      :entity-type :data-table
                      :identity identity
                      :entities [data-table])
                     first
                     ent/un-store
                     created))

           (GET "/external_id/:external_id" []
                :return PartialDataTableList
                :query [q DataTableByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List data-tables by external id"
                :capabilities #{:read-data-table :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :data-table
                                list-data-tables
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe PartialDataTable)
                :summary "Gets a Data Table by ID"
                :path-params [id :- s/Str]
                :query [params DataTableGetParams]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-data-table
                :identity identity
                :identity-map identity-map
                (if-let [data-table (read-store :data-table
                                                read-data-table
                                                id
                                                identity-map
                                                params)]
                  (-> data-table
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes a Data Table"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-data-table
                   :identity identity
                   :identity-map identity-map
                   (if (flows/delete-flow
                        :get-fn #(read-store :data-table
                                             read-data-table
                                             %
                                             identity-map
                                             {})
                        :delete-fn #(write-store :data-table
                                                 delete-data-table
                                                 %
                                                 identity-map)
                        :entity-type :data-table
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
