(ns ctia.coa.store.es
  (:require
   [ctia.store :refer [IStore IQueryStringSearchableStore]]
   [ctia.stores.es.crud :as crud]
   [ctia.schemas.core
    :refer [StoredCOA PartialStoredCOA]]))

(def handle-create (crud/handle-create :coa StoredCOA))
(def handle-read (crud/handle-read :coa PartialStoredCOA))
(def handle-update (crud/handle-update :coa StoredCOA))
(def handle-delete (crud/handle-delete :coa StoredCOA))
(def handle-list (crud/handle-find :coa PartialStoredCOA))
(def handle-query-string-search (crud/handle-query-string-search :coa PartialStoredCOA))

(defrecord COAStore [state]
  IStore
  (read [_ id ident params]
    (handle-read state id ident params))
  (create [_ new-coas ident params]
    (handle-create state new-coas ident params))
  (update [_ id new-coa ident]
    (handle-update state id new-coa ident))
  (delete [_ id ident]
    (handle-delete state id ident))
  (list [_ filter-map ident params]
    (handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search state query filtermap ident params)))
