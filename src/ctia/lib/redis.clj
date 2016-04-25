(ns ctia.lib.redis
  (:require [taoensso.carmine :as c]))

;; Connections

(defn server-connection
  "Build the server config"
  [[host port]]
  (assert (and host port) "Redis has been de-configured")
  {:pool {}
   :spec {:host host
          :port port
          :timeout-ms 30000}})

;; Pub/Sub

(defn unsubscribe [pubsub-listener]
  (c/with-open-listener pubsub-listener
    (c/unsubscribe)))

(defn subscribe [host-port event-channel-name listener-fn]
  (c/with-new-pubsub-listener (:spec (server-connection host-port))
    {event-channel-name listener-fn}
    (c/subscribe event-channel-name)))

(defn publish [host-port event-chanel-name event]
  (c/wcar (server-connection host-port)
    (c/publish event-chanel-name event)))

(defn close-listener [pubsub-listener]
  (c/close-listener pubsub-listener))
