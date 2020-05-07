(ns ctia.entity.event.store
  (:require
   [ctia.entity.event.schemas :refer [PartialEvent]]
   [ctia.store :refer :all]
   [ctia.entity.event.crud :as crud]
   [ctia.store :refer [IEventStore]]))

(defrecord EventStore [state]
  IStore
  (read-record [_ id ident params]
    (crud/handle-read state id ident params))
  IEventStore
  (create-events [this new-events]
    (crud/handle-create state new-events))
  (list-events [this filter-map ident params]
    (crud/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ search-query ident params]
    (crud/handle-event-query-string-search
     state search-query ident params))
  (aggregate [_ search-query agg-query ident]
    (crud/handle-aggregate
     state search-query agg-query ident)))
