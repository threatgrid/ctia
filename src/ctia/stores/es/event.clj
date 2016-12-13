(ns ctia.stores.es.event
  (:require [ctia.lib.es
             [document :as d]
             [schemas :refer [ESConnState SliceProperties]]
             [slice :refer [get-slice-props]]]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.schemas.event :as ctia-event-schemas]
            [ctia.stores.es.crud :as crud]
            [ctim.events.schemas :as event-schemas]
            [schema.core :as s]))

(s/defschema UpdateMap
  "a map converted from an Update Triple for ES Compat"
  {:field s/Keyword
   :action s/Str
   :change {s/Any s/Any}})

(s/defn update-triple->map :- UpdateMap
  [[field action change] :- event-schemas/UpdateTriple]
  {:field field
   :action action
   :change change})

(s/defn transform-fields [e :- event-schemas/Event]
  "for ES compat, transform field vector of an event to a map"
  (if-let [fields (:fields e)]
    (assoc e :fields (map update-triple->map fields)) e))

(defn attach-bulk-fields [index event]
  (assoc event
         :_type "event"
         :_id (:id event)
         :_index index))

(s/defn index-produce
  "produce an event to an aliased index"
  [state :- ESConnState
   slice-props :- SliceProperties
   events :- [event-schemas/Event]]
  (d/bulk-create-doc (:conn state)
                     (->> events
                          (map transform-fields)
                          (map (partial attach-bulk-fields (:name slice-props))))
                     (get-in state [:props :refresh] false)))

(s/defn simple-produce
  "produce an event to an index"
  [state :- ESConnState
   events :- [event-schemas/Event]]
  (d/bulk-create-doc (:conn state)
                     (->> events
                          (map transform-fields)
                          (map (partial attach-bulk-fields (:index state))))
                     (get-in state [:props :refresh] false)))

(s/defn produce
  ([state :- ESConnState
    slice-props :- SliceProperties
    events :- [event-schemas/Event]]
   (index-produce state slice-props events))

  ([state :- ESConnState
    events :- [event-schemas/Event]]
   (simple-produce state events)))

(s/defn handle-create :- [event-schemas/Event]
  "produce an event to ES"
  [state :- ESConnState
   events :- [event-schemas/Event]]
  (if (-> state :slicing :strategy)
    (let [slice-props (get-slice-props (:timestamp (first events)) state)]
      (produce state slice-props events))
    (produce state events))
  events)

(def ^:private handle-list-raw (crud/handle-find :event event-schemas/Event))

(defn coerce-event! [event]
  ((crud/coerce-to-fn (ctia-event-schemas/schema-for-event event))
   event))

(s/defn handle-list :- (list-response-schema event-schemas/Event)
  [state :- ESConnState
   filter-map :- {s/Any s/Any}
   params]
  (update (handle-list-raw state filter-map params)
          :data #(map coerce-event! %)))
