(ns ctia.events.producers.es.producer
  (:require
   [schema.core :as s]
   [clojurewerkz.elastisch.native.document :as document]
   [ctia.lib.es.index :refer [ESConnState]]
   [ctia.events.schemas :refer [Event]]
   [ctia.events.producer :refer [IEventProducer]]))

(def mapping "event")

(s/defn handle-produce-event
  [state :- ESConnState
   event :- Event]

  (document/create (:conn state)
                   (:index-name state)
                   mapping
                   event
                   :refresh true))

(defrecord EventProducer [state]
  IEventProducer
  (produce-event [_ event]
    (handle-produce-event state event)))
