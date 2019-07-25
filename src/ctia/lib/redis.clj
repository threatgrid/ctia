(ns ctia.lib.redis
  (:require [taoensso.carmine :as c]))

;; Connections

(defn redis-conf->conn-spec
  [{:keys [host port db timeout-ms password ssl]}]
  (cond-> {:host host
           :port port}
    timeout-ms (assoc :timeout-ms timeout-ms)
    password (assoc :password password)
    db (assoc :db db)
    ssl (assoc :ssl-fn :default)))

(defn server-connection
  "Build the server config"
  [{:keys [host port] :as redis-config}]
  (assert (and host port) "Redis has been de-configured")
  {:pool {}
   :spec (redis-conf->conn-spec redis-config)})

;; Pub/Sub

(defn unsubscribe [pubsub-listener]
  (c/with-open-listener pubsub-listener
    (c/unsubscribe)))

(defn subscribe [{spec :spec} event-channel-name listener-fn]
  (c/with-new-pubsub-listener spec
    {event-channel-name listener-fn}
    (c/subscribe event-channel-name)))

(defn subscribe-to [conn event-channel-name listener-fn pred]
  (subscribe conn
             event-channel-name
             (fn [[command _ payload]]
               (when (pred command)
                 (listener-fn payload)))))

(defn subscribe-to-messages [conn event-channel-name listener-fn]
  (subscribe-to conn event-channel-name listener-fn (partial = "message")))

(defn publish [conn event-chanel-name event]
  (c/wcar conn
    (c/publish event-chanel-name event)))

(defn close-listener [pubsub-listener]
  (c/close-listener pubsub-listener))
