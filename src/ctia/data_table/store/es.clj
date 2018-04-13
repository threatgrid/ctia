(ns ctia.data-table.store.es
  (:require [ctia.schemas.core :refer [PartialStoredDataTable StoredDataTable]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store DataTableStore :data-table StoredDataTable PartialStoredDataTable)
