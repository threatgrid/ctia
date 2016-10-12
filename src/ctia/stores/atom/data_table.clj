(ns ctia.stores.atom.data-table
  (:require [ctia.schemas.core :refer [StoredDataTable]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-data-table (mc/create-handler-from-realized StoredDataTable))
(def handle-read-data-table (mc/read-handler StoredDataTable))
(def handle-delete-data-table (mc/delete-handler StoredDataTable))
(def handle-list-data-tables (mc/list-handler StoredDataTable))
