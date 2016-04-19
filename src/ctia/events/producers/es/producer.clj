(ns ctia.events.producers.es.producer
  (:require
   [schema.core :as s]
   [ctia.properties :refer [properties]]
   [ctia.lib.es.index :refer [ESConnState connect]]
   [ctia.lib.es.document :refer [create-doc-fn]]
   [ctia.events.producers.es.mapping :refer [producer-mappings]]
   [ctia.lib.es.slice :refer [create-slice!
                              get-slice-props]]
   [ctia.events.schemas :refer [Event]]
   [ctia.events.producer :refer [IEventProducer]]))

(defn read-producer-index-spec []
  "read es producer index config properties, returns an option map"
  (get-in @properties [:ctia :producer :es]))

(s/defn init-producer-conn :- ESConnState []
  "initiate an ES producer connection returns a map containing transport,
   mapping and the configured index name"
  (let [props (read-producer-index-spec)]
    {:index (:indexname props)
     :props props
     :mapping producer-mappings
     :conn (connect props)}))

(defn field-vec->field-map [[field action change]]
  {:field field
   :action action
   :change change})

(s/defn transform-fields [e :- Event]
  "for ES compat, transform field
   vector of the event to a map"
  (if-let [fields (:fields e)]
    (assoc e :fields (map field-vec->field-map fields)) e))

(defn index-produce
  "produce an event to an aliased index"
  [state event slice-props]

  (:_id ((create-doc-fn (:conn state))
         (:conn state)
         (:name slice-props)
         "event"
         event)))

(defn alias-produce
  "produce an event to a
   filtered alias of an index"
  [state event slice-props]

  (:_id ((create-doc-fn (:conn state))
         (:conn state)
         (:index state)
         "event"
         event
         :routing (:name slice-props))))

(s/defn handle-produce-event :- s/Str
  "produce an event to ES"
  [state :- ESConnState
   event :- Event]

  (let [e (transform-fields event)
        slice-props (get-slice-props (:timestamp e)
                                     state)]

    (create-slice! state slice-props)

    (if (= :filtered-alias (:strategy slice-props))
      (alias-produce state e slice-props)
      (index-produce state e slice-props))))

(defrecord EventProducer [state]
  IEventProducer
  (produce-event [_ event]
    (handle-produce-event state event)))
