(ns ctia.entity.data-table
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [def-acl-schema def-stored-schema]]
             [sorting :refer [default-entity-sort-fields]]]
            [ctia.store :refer :all]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [ctim.schemas.data-table :as ds]
            [flanders.utils :as fu]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def-acl-schema DataTable
  (fu/replace-either-with-any
   ds/DataTable)
  "data-table")

(def-acl-schema PartialDataTable
  (fu/optionalize-all
   (fu/replace-either-with-any
    ds/DataTable))
  "partial-data-table")

(s/defschema PartialDataTableList
  [PartialDataTable])

(def-acl-schema NewDataTable
  (fu/replace-either-with-any
   ds/NewDataTable)
  "new-data-table")

(def-stored-schema StoredDataTable
  (fu/replace-either-with-any
   ds/StoredDataTable)
  "stored-data-table")

(def-stored-schema PartialStoredDataTable
  (fu/optionalize-all
   (fu/replace-either-with-any
    ds/StoredDataTable))
  "partial-stored-data-table")

(def realize-data-table
  (default-realize-fn "data-table" NewDataTable StoredDataTable))

(def datatable-sort-fields
  (apply s/enum default-entity-sort-fields))

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

(def data-table-mapping
  {"data-table"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:valid_time em/valid-time
      :row_count {:type "long"}
      :columns {:enabled false}
      :rows {:enabled false}})}})

(def-es-store DataTableStore :data-table StoredDataTable PartialStoredDataTable)

(def capabilities
  #{:create-data-table
    :read-data-table
    :delete-data-table
    :search-data-table})

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

(def data-table-entity
  {:route-context "/data-table"
   :tags ["DataTable"]
   :entity :data-table
   :plural :data_tables
   :schema DataTable
   :partial-schema PartialDataTable
   :partial-list-schema PartialDataTableList
   :new-schema NewDataTable
   :stored-schema StoredDataTable
   :partial-stored-schema PartialStoredDataTable
   :realize-fn realize-data-table
   :es-store ->DataTableStore
   :es-mapping data-table-mapping
   :routes data-table-routes
   :capabilities capabilities})
