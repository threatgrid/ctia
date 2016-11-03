(ns ctia.http.routes.data-table
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :refer [realize-data-table]]
   [ctia.domain.entities.data-table :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :refer [created paginated-ok PagingParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewDataTable StoredDataTable]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema DataTableByExternalIdQueryParams
  (st/merge
   PagingParams
   {:external_id s/Str}))

(defroutes data-table-routes
  (context "/data-table" []
           :tags ["DataTable"]
           (POST "/" []
                 :return StoredDataTable
                 :body [data-table NewDataTable {:description "a new Data Table"}]
                 :header-params [api_key :- (s/maybe s/Str)]
                 :summary "Adds a new Data Table"
                 :capabilities :create-data-table
                 :identity identity
                 (created
                  (with-long-id
                    (first
                     (flows/create-flow
                      :entity-type :data-table
                      :realize-fn realize-data-table
                      :store-fn #(write-store :data-table create-data-tables %)
                      :entity-type :data-table
                      :identity identity
                      :entities [data-table])))))
           (GET "/external_id" []
                :return [(s/maybe StoredDataTable)]
                :query [q DataTableByExternalIdQueryParams]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "List data-tables by external id"
                :capabilities #{:read-data-table :external-id}
                (paginated-ok
                 (page-with-long-id
                  (read-store :data-table list-data-tables
                              {:external_ids (:external_id q)} q))))

           (GET "/:id" []
                :return (s/maybe StoredDataTable)
                :summary "Gets a Data Table by ID"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :read-data-table
                (if-let [d (read-store :data-table read-data-table id)]
                  (ok (with-long-id d))
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
