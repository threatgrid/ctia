(ns ctia.events.producers.es.producer
  (:require
   [schema.core :as s]
   [clojurewerkz.elastisch.native.document :as document]
   [ctia.events.schemas :refer [Event]]
   [ctia.events.producer :refer [IEventProducer]]))

(def mapping "event")

(s/defn handle-produce-event
  [state event]

  (clojure.pprint/pprint state)
  (clojure.pprint/pprint event)

  (document/create (:conn state)
                   (:index-name state)
                   mapping
                   event
                   :refresh true))

(defrecord EventProducer [state]
  IEventProducer
  (produce-event [_ event]
    (handle-produce-event state event)))
