(ns ctia.stores.redis.store
  "Central setup for redis."
  (:require [taoensso.carmine :as c]
            [ctia.properties :as p]
            [ctia.events :as e]
            [schema.core :as s :refer [=>]])
  (:import [java.net URL]))

;; URL for connecting to Redis. Read from properties.
(defonce redis-url
  (if-let [u (get p/properties "ctia.store.redis.uri")]
    (URL. u)))

(def default-port "The default port to use for Redis when not configured" 6379)

;; The port number for connecting to Redis.
;; Prefers the Redis URL, then a configured port number, and finally the default
(defonce port
  (if redis-url
    (.getPort redis-url)
    (get p/properties "ctia.store.redis.port" default-port)))

(def default-host "The default address to connect to Redis at" "127.0.0.1")

;; The Redis host.
;; Prefers the Redis URL, then a configured address, and finally the default of localhost
(defonce host
  (if redis-url
    (.getHost redis-url)
    (get p/properties "ctia.store.redis.host" default-host)))

(defonce server1-conn {:pool {}
                       :spec {:host host :port port}})

(defmacro wcar
  "Provides the context for executing Redis commands, using the configured server."
  [& body] `(c/wcar server1-conn ~@body))

(def event-channel-name "The name of the channel for pub/sub on Redis" "event")

(s/defn publish-fn
  "Callback function that publishes events to Redis."
  [event :- e/Event]
  (wcar (c/publish event-channel-name event)))

(def pubsub-listener "Central listener for subscribing to Redis." (atom nil))

(defn close!
  "Closes the central Redis subscription listener"
  [] (when @pubsub-listener
                  (c/close-listener @pubsub-listener)))

(defn set-listener-fn!
  "Sets the function to be called when events are published into Redis.
   Closes and replaces the previous listening function, if there was one."
  [listener-fn]
  (close!)
  (reset! pubsub-listener
          (c/with-new-pubsub-listener (:spec server1-conn)
            {event-channel-name listener-fn}
            (c/subscribe event-channel-name))))
