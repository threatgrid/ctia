(ns ctia.stores.es.data-table
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredDataTable]]))

(def handle-create-data-table (crud/handle-create :data-table StoredDataTable))
(def handle-read-data-table (crud/handle-read :data-table StoredDataTable))
(def handle-delete-data-table (crud/handle-delete :data-table StoredDataTable))
(def handle-list-data-tables (crud/handle-find :data-table StoredDataTable))
