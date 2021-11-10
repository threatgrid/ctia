(ns ctia.entity.data-table
  (:require
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.schemas.core :refer [APIHandlerServices def-acl-schema def-stored-schema]]
   [ctia.schemas.sorting :refer [default-entity-sort-fields]]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
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

(def-stored-schema StoredDataTable DataTable)

(s/defschema PartialStoredDataTable
  (st/optional-keys-schema StoredDataTable))

(def realize-data-table
  (default-realize-fn "data-table" NewDataTable StoredDataTable))

(def datatable-sort-fields
  (apply s/enum default-entity-sort-fields))

(s/defschema DataTableFieldsParam
  {(s/optional-key :fields) [datatable-sort-fields]})

(s/defschema DataTableSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   DataTableFieldsParam
   (st/optional-keys
    {:sort_by datatable-sort-fields})))

(def DataTableGetParams DataTableFieldsParam)

(s/defschema DataTableByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   DataTableFieldsParam))

(def data-table-mapping
  {"data-table"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:valid_time em/valid-time
      :row_count  em/long-type
      :columns    {:enabled false}
      :rows       {:enabled false}})}})

(def-es-store DataTableStore :data-table StoredDataTable PartialStoredDataTable)

(def capabilities
  #{:create-data-table
    :read-data-table
    :delete-data-table
    :search-data-table})

(s/defn data-table-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :data-table
    :new-spec                 :new-data-table/map
    :new-schema               NewDataTable
    :entity-schema            DataTable
    :get-schema               PartialDataTable
    :get-params               DataTableGetParams
    :list-schema              PartialDataTableList
    :external-id-q-params     DataTableByExternalIdQueryParams
    :search-q-params          DataTableSearchParams
    :realize-fn               realize-data-table
    :get-capabilities         :read-data-table
    :post-capabilities        :create-data-table
    :delete-capabilities      :delete-data-table
    :external-id-capabilities :read-data-table
    :can-update?              false
    :can-search?              false}))

(def data-table-entity
  {:route-context         "/data-table"
   :tags                  ["DataTable"]
   :entity                :data-table
   :plural                :data-tables
   :new-spec              :new-data-table/map
   :schema                DataTable
   :partial-schema        PartialDataTable
   :partial-list-schema   PartialDataTableList
   :new-schema            NewDataTable
   :stored-schema         StoredDataTable
   :partial-stored-schema PartialStoredDataTable
   :realize-fn            realize-data-table
   :es-store              ->DataTableStore
   :es-mapping            data-table-mapping
   :services->routes      (routes.common/reloadable-function data-table-routes)
   :capabilities          capabilities})
