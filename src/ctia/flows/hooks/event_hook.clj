(ns ctia.flows.hooks.event-hook
  (:require
    [clojure.tools.logging :as log]
    [ctia.flows.hook-protocol :refer [Hook]]
    [ctia.events.producers.es.producer :as esp]))

(defrecord ESEventProducerRecord [conn]
  Hook
  (init [_]
    (reset! conn (esp/init-producer-conn)))
  (destroy [_]
    (reset! conn nil))
  (handle [_ _ object _]
    (try
      (when (some? @conn)
        (esp/handle-produce-event @conn object))
      (catch Exception e
        ;; Should we really be swallowing exceptions?
        (log/error "Exception while producing event" e)))))

(def es-event-producer-hook
  (->ESEventProducerRecord (atom {})))
