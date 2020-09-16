(ns ctia.entity.event.crud
  (:require
   [schema.core :as s]
   [ctia.entity.event.schemas
    :refer [Event PartialEvent]]
   [ctia.stores.es.crud :as crud]
   [ctia.stores.es.schemas :refer [ESConnState]]))

(def ^:private handle-create-fn
  (crud/handle-create :event Event))

(s/defn handle-create :- [Event]
  "produce an event to ES"
  [conn :- ESConnState
   events :- [Event]]
  (handle-create-fn conn
                    events
                    {}
                    {}))

(def handle-list
  (crud/handle-find Event))

(def handle-read
  (crud/handle-read PartialEvent))

(def handle-event-query-string-search
  (crud/handle-query-string-search PartialEvent))

(def handle-event-query-string-count crud/handle-query-string-count)

(def handle-aggregate crud/handle-aggregate)
