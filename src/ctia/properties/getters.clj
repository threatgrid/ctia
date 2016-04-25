(ns ctia.properties.getters
  (:require [clojure.core.memoize :as memo])
  (:import [java.net URI]))

(def default-redis-port
  "The default port to use for Redis when not configured"
  6379)

(def default-redis-host
  "The default address to connect to Redis at"
  "127.0.0.1")

(defn redis-host-port*
  "Reads a host/port pair from a properties map"
  [props]
  (let [redis (get-in props [:ctia :store :redis])
        redis-url (if-let [u (:uri redis)] (URI. u))]
    (if redis-url
      [(.getHost redis-url) (.getPort redis-url)]
      [(get redis :host default-redis-host)
       (get redis :port default-redis-port)])))

;; cache property configurations to a modest level (rarely need more than 1)
(def redis-host-port (memo/fifo redis-host-port* :fifo/threshold 8))
