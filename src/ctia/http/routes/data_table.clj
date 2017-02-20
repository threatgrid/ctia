(ns ctia.http.routes.data-table
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.data-table :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :refer [created paginated-ok PagingParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewDataTable DataTable]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema DataTableByExternalIdQueryParams
  PagingParams)

(defroutes data-table-routes
  (context "/data-table" []
           :tags ["DataTable"]
           (POST "/" []
                 :return DataTable
                 :body [data-table NewDataTable {:description "a new Data Table"}]
                 :header-params [api_key :- (s/maybe s/Str)]
                 :summary "Adds a new Data Table"
                 :capabilities :create-data-table
                 :identity identity
                 (-> (flows/create-flow
                      :entity-type :data-table
                      :realize-fn ent/realize-data-table
                      :store-fn #(write-store :data-table create-data-tables %)
                      :long-id-fn with-long-id
                      :entity-type :data-table
                      :identity identity
                      :entities [data-table])
                     first
                     ent/un-store
                     created))

           (GET "/external_id/:external_id" []
                :return [(s/maybe DataTable)]
                :query [q DataTableByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "List data-tables by external id"
                :capabilities #{:read-data-table :external-id}
                (-> (read-store :data-table list-data-tables
                                {:external_ids external_id} q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe DataTable)
                :summary "Gets a Data Table by ID"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :read-data-table
                (if-let [data-table (read-store :data-table read-data-table id)]
                  (-> data-table
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes a Data Table"
                   :header-params [api_key :- (s/maybe s/Str)]
                   :capabilities :delete-data-table
                   :identity identity
                   (if (flows/delete-flow
                        :get-fn #(read-store :data-table read-data-table %)
                        :delete-fn #(write-store :data-table delete-data-table %)
                        :entity-type :data-table
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
