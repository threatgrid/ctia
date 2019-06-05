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

(s/defn index-produce
  "produce an event to an aliased index"
  [state :- ESConnState
   slice-props :- SliceProperties
   events :- [Event]]
  (document/bulk-create-doc
   (:conn state)
   events
   (get-in state [:props :refresh] false)))

(s/defn simple-produce
  "produce an event to an index"
  [state :- ESConnState
   events :- [Event]]
  (document/bulk-create-doc
   (:conn state)
   (->> events
        (map (partial attach-bulk-fields (:index state))))
   (get-in state [:props :refresh] false)))

(s/defn produce
  ([state :- ESConnState
    slice-props :- SliceProperties
    events :- [Event]]
   (index-produce state slice-props events))

  ([state :- ESConnState
    events :- [Event]]
   (simple-produce state events)))

(s/defn handle-create :- [Event]
  "produce an event to ES"
  [state :- ESConnState
   events :- [Event]]
  (if (-> state :slicing :strategy)
    (let [slice-props (get-slice-props (:timestamp (first events)) state)]
      (produce state slice-props events))
    (produce state events))
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
