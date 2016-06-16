(ns ctia.http.middleware.metrics
  "Middleware to control all metrics of the server"
  (:require [metrics.core :refer [new-registry]]
            [metrics.meters :refer [meter]]
            [metrics.ring.expose :refer [expose-metrics-as-json]]
            [metrics.ring.instrument :refer [instrument]]))

(defn wrap-metrics [handler]
  (-> handler
      (expose-metrics-as-json "/stats") ;; WARNING PUBLIC
      (instrument)))
