(ns ctia.entity.event.crud
  (:require
   [schema.core :as s]
   [ctia.entity.event.schemas
    :refer [Event PartialEvent]]
   [ctia.stores.es.crud :as crud]
   [clj-momo.lib.es
    [document :as document]
    [schemas :refer [ESConnState SliceProperties]]
    [slice :refer [get-slice-props]]]))

(defn attach-bulk-fields [index event]
  (assoc event
         :_type "event"
         :_id (:id event)
         :_index index))

(s/defn handle-create :- [Event]
  "produce an event to ES"
  [{:keys [conn props]} :- ESConnState
   events :- [Event]]
  (document/bulk-create-doc
   conn
   (map #(attach-bulk-fields (:write-alias props) %)
        events)
   (:refresh props "false"))
  events)

(def ^:private handle-list-fn
  (crud/handle-find :event Event))

(s/defn handle-list
  [state :- ESConnState
   filter-map :- crud/FilterSchema
   ident
   params]
  (handle-list-fn state
                  filter-map
                  ident
                  params))

(def handle-event-query-string-search
  (crud/handle-query-string-search
   "event" PartialEvent))
