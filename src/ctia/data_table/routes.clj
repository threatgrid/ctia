(ns ctia.data-table.routes
  (:require [ctia.domain.entities :refer [realize-data-table]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [DataTable NewDataTable PartialDataTable PartialDataTableList]]
             [sorting :as sorting]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def datatable-sort-fields
  (apply s/enum sorting/default-entity-sort-fields))

(s/defschema DataTableFieldsParam
  {(s/optional-key :fields) [datatable-sort-fields]})

(s/defschema DataTableSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   DataTableFieldsParam
   {:query s/Str
    (s/optional-key :sort_by) datatable-sort-fields}))

(def DataTableGetParams DataTableFieldsParam)

(s/defschema DataTableByExternalIdQueryParams
  (st/merge
   PagingParams
   DataTableFieldsParam))

(def data-table-routes
  (entity-crud-routes
   {:entity :data-table
    :new-schema NewDataTable
    :entity-schema DataTable
    :get-schema PartialDataTable
    :get-params DataTableGetParams
    :list-schema PartialDataTableList
    :external-id-q-params DataTableByExternalIdQueryParams
    :search-q-params DataTableSearchParams
    :realize-fn realize-data-table
    :get-capabilities :read-data-table
    :post-capabilities :create-data-table
    :delete-capabilities :delete-data-table
    :external-id-capabilities #{:read-data-table :external-id}
    :can-update? false
    :can-search? false}))
