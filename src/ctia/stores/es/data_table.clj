(ns ctia.stores.es.data-table
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredDataTable]]))

(def handle-create (crud/handle-create :data-table StoredDataTable))
(def handle-read (crud/handle-read :data-table StoredDataTable))
(def handle-delete (crud/handle-delete :data-table StoredDataTable))
(def handle-list (crud/handle-find :data-table StoredDataTable))
