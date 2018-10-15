(ns ctia.entity.event.store
  (:require
   [ctia.store :refer :all]
   [ctia.entity.event.crud
    :refer [handle-list
            handle-create
            handle-event-query-string-search]]
   [ctia.stores.es.crud
    :refer [handle-query-string-search]]
   [ctia.store :refer [IEventStore]]))

(defrecord EventStore [state]
  IEventStore
  (create-events [this new-events]
    (handle-create state new-events))
  (list-events [this filter-map ident params]
    (handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-event-query-string-search
     state query filtermap ident params)))
