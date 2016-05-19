(ns ctia.events.producers.es.producer
  (:require [ctia.events.producers.es.mapping :refer [producer-mappings]]
            [ctia.events.schemas :refer [Event UpdateTriple]]
            [ctia.lib.es
             [document :refer [create-doc-fn]]
             [index :refer [connect ESConnState]]
             [slice :refer [ensure-slice-created! get-slice-props SliceProperties]]]
            [ctia.properties :refer [properties]]
            [schema.core :as s]))

(s/defn init-producer-conn :- (s/maybe ESConnState) []
  "initiate an ES producer connection returns a map containing transport,
   mapping and the configured index name"
  (when-let [{:keys [indexname] :as props} (get-in @properties [:ctia :hook :es])]
    {:index indexname
     :props props
     :mapping producer-mappings
     :conn (connect props)}))

(s/defschema UpdateMap
  "a map converted from an Update Triple for ES Compat"
  {:field s/Keyword
   :action s/Str
   :change {s/Any s/Any}})

(s/defn update-triple->map :- UpdateMap
  [[field action change] :- UpdateTriple]
  {:field field
   :action action
   :change change})

(s/defn transform-fields [e :- Event]
  "for ES compat, transform field vector of an event to a map"
  (if-let [fields (:fields e)]
    (assoc e :fields (map update-triple->map fields)) e))

(s/defn index-produce
  "produce an event to an aliased index"
  [state :- ESConnState
   slice-props :- SliceProperties
   event :- Event]

  (:_id ((create-doc-fn (:conn state))
         (:conn state)
         (:name slice-props)
         "event"
         (transform-fields event))))

(s/defn alias-produce :- s/Str
  "produce an event to a filtered alias of an index"
  [state :- ESConnState
   slice-props :- SliceProperties
   event :- Event]

  (:_id ((create-doc-fn (:conn state))
         (:conn state)
         (:index state)
         "event"
         (transform-fields event)
         :routing (:name slice-props))))

(s/defn simple-produce :- s/Str
  "produce an event to an index"
  [state :- ESConnState
   event :- Event]
  (:_id ((create-doc-fn (:conn state))
         (:conn state)
         (:index state)
         "event"
         (transform-fields event))))

(s/defn produce :- s/Str
  ([state :- ESConnState
    slice-props :- SliceProperties
    event :- Event]
   (case (:strategy slice-props)
     :filtered-alias (alias-produce state slice-props event)
     :aliased-index (index-produce state slice-props event)))

  ([state :- ESConnState
    event :- Event]
   (simple-produce state event)))

(s/defn handle-produce-event :- s/Str
  "produce an event to ES"
  [state :- ESConnState
   event :- Event]

  (if (-> state :slicing :strategy)
    (let [slice-props (get-slice-props (:timestamp event) state)]
      (ensure-slice-created! state slice-props)
      (produce state slice-props event))
    (produce state event)))
