(ns ctia.events.producer
  (:require
   [schema.core :as s]
   [ctia.events.schemas :refer [Event]]))

(def event-producers (atom []))

(defprotocol IEventProducer
  (produce-event [this event]))

(s/defn produce [e :- Event]
  "Produce an event
   triggers all registered event producers"

  (doall
   (pmap #(produce-event % e) @event-producers)))
