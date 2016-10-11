(ns ctia.stores.atom.data-table
  (:require [ctim.schemas.data-table :refer [StoredDataTable]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-data-table (mc/create-handler-from-realized StoredDataTable))
(def handle-read-data-table (mc/read-handler StoredDataTable))
(def handle-delete-data-table (mc/delete-handler StoredDataTable))
