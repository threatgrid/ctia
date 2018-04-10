(ns ctia.indicator.store.es
  (:require
   [ctia.store :refer [IStore IQueryStringSearchableStore]]
   [ctia.schemas.core
    :refer [StoredIndicator PartialStoredIndicator]]
   [ctia.stores.es.crud :as crud]))

(def handle-create (crud/handle-create :indicator StoredIndicator))
(def handle-read (crud/handle-read :indicator PartialStoredIndicator))
(def handle-update (crud/handle-update :indicator StoredIndicator))
(def handle-delete (crud/handle-delete :indicator StoredIndicator))
(def handle-list (crud/handle-find :indicator PartialStoredIndicator))
(def handle-query-string-search (crud/handle-query-string-search :indicator PartialStoredIndicator))

(defrecord IndicatorStore [state]
  IStore
  (create [_ new-indicators ident params]
    (handle-create state new-indicators ident params))
  (update [_ id new-indicator ident]
    (handle-update state id new-indicator ident))
  (read [_ id ident params]
    (handle-read state id ident params))
  (delete [_ id ident]
    (handle-delete state id ident))
  (list [_ filter-map ident params]
    (handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search state query filtermap ident params)))
