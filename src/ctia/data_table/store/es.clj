(ns ctia.data-table.store.es
  (:require
   [ctia.store :refer [IStore]]
   [ctia.stores.es.crud :as crud]
   [ctia.schemas.core :refer [StoredDataTable]]))

(def handle-create (crud/handle-create :data-table StoredDataTable))
(def handle-read (crud/handle-read :data-table StoredDataTable))
(def handle-delete (crud/handle-delete :data-table StoredDataTable))
(def handle-list (crud/handle-find :data-table StoredDataTable))

(defrecord DataTableStore [state]
  IStore
  (read [_ id ident params]
    (handle-read state id ident params))
  (create [_ new-data-tables ident params]
    (handle-create state new-data-tables ident params))
  (delete [_ id ident]
    (handle-delete state id ident))
  (list [_ filter-map ident params]
    (handle-list state filter-map ident params)))
