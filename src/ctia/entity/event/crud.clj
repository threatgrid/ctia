(ns ctia.entity.event.crud
  (:require
   [schema.core :as s]
   [ctia.entity.event.schemas
    :refer [Event PartialEvent]]
   [ctia.stores.es.crud :as crud]
   [clj-momo.lib.es.schemas :refer [ESConnState]]))

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
  (crud/handle-find :event Event))

(def handle-read
  (crud/handle-read :event PartialEvent))

(def handle-event-query-string-search
  (crud/handle-query-string-search
   :event PartialEvent))

(def handle-event-query-string-count
  (crud/handle-query-string-count
   :event))

(def handle-aggregate
  (crud/handle-aggregate :event))
