(ns ctia.events.producer)

(defprotocol IEventProducer
  (produce-event [this event]))

(def event-producers (atom []))
