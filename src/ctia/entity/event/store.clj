(ns ctia.entity.event.store
  (:require
   [ctia.entity.event.schemas :refer [PartialEvent]]
   [ctia.stores.es.crud :as es-crud]
   [ctia.store :refer :all]
   [ctia.entity.event.crud
    :refer [handle-list
            handle-create
            handle-event-query-string-search]]
   [ctia.store :refer [IEventStore]]))

(defrecord EventStore [state]
  IStore
  (read-record [_ id ident params]
    ((es-crud/handle-read "event" PartialEvent) state id ident params))
  IEventStore
  (create-events [this new-events]
    (handle-create state new-events))
  (list-events [this filter-map ident params]
    (handle-list state filter-map ident params))

  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-event-query-string-search
     state query filtermap ident params)))
