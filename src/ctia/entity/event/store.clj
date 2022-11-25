(ns ctia.entity.event.store
  (:require
   [ctia.store :refer [IStore IQueryStringSearchableStore IEventStore]]
   [ctia.entity.event.crud :as crud]
   [ctia.stores.es.store :refer [close-connections!]]))

(defrecord EventStore [state]
  IStore
  (read-record [_ id ident params]
    (crud/handle-read state id ident params))
  (list-records [_ filtermap ident params]
    (crud/handle-list state filtermap ident params))
  IEventStore
  (create-events [_ new-events]
    (crud/handle-create state new-events))
  (list-events [_ filter-map ident params]
    (crud/handle-list state filter-map ident params))
  (close [_] (close-connections! state))
  IQueryStringSearchableStore
  (query-string-search [_ search-query ident params]
    (crud/handle-event-query-string-search
     state search-query ident params))
  (query-string-count [_ search-query ident]
    (crud/handle-event-query-string-count
     state search-query ident))
  (aggregate [_ search-query agg-query ident]
    (crud/handle-aggregate
     state search-query agg-query ident))
  (delete-search [_ search-query ident params]
    (crud/handle-delete-search
     state search-query ident params)))
