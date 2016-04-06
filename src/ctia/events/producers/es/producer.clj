(ns ctia.events.producers.es.producer
  (:require
   [schema.core :as s]
   [clojurewerkz.elastisch.native.document :as document]
   [ctia.lib.es.index :refer [ESConnState]]
   [ctia.events.schemas :refer [Event]]
   [ctia.events.producer :refer [IEventProducer]]))


(s/defn transform-fields [e :- Event]
  "for ES compat, transform field data to a map"
  (if-let [fields (:fields e)]
    (assoc e :fields
           (map #(hash-map :field (first %)
                           :action (second %)
                           :change (last %)) fields))

    e))

(s/defn handle-produce-event :- s/Str
  "given a conn state and an event write the event to ES"
  [state :- ESConnState
   event :- Event]

  (->> event
       transform-fields
       (document/create (:conn state)
                        (:index state)
                        "event")

       :id))

(defrecord EventProducer [state]
  IEventProducer
  (produce-event [_ event]
    (handle-produce-event state event)))
