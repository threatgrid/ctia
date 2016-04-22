(ns ctia.stores.redis.store
  "Central setup for redis."
  (:require [ctia.lib.async :as la]
            [ctia.lib.redis :as lr]
            [ctia.properties :refer [properties]]
            [ctia.properties.getters :as pget]
            [clojure.tools.logging :as log]
            [schema.core :as s])
  (:import [java.io IOException]))

(def event-channel-name
  "The name of the channel for pub/sub on Redis"
  "event")

(defonce pubsub-listener
  ^{:doc "Central listener for subscribing to Redis."}
  (atom nil))

(defn enabled?
  "Returns true when Redis is currently configured"
  []
  (get-in @properties [:ctia :store :redis :enabled]))

(s/defn publish-fn
  "Callback function that publishes events to Redis."
  [event :- la/Event]
  (when (enabled?)
    (lr/publish (pget/redis-host-port @properties)
                event-channel-name
                event)))

(defn set-listener-fn!
  "Sets the function to be called when events are published into Redis.
   This is initialized once, and is never changed until shutdown."
  [listener-fn]
  {:pre [(nil? @pubsub-listener)]}
  (when (enabled?)
    (reset! pubsub-listener
            (lr/subscribe (pget/redis-host-port @properties)
                          event-channel-name
                          listener-fn))))

(defn shutdown!
  "Closes the central Redis subscription listener.
   Returns true when a listener was actually closed."
  []
  (when @pubsub-listener
    (try
      (lr/unsubscribe @pubsub-listener)
      (lr/close-listener @pubsub-listener)
      (catch IOException e
        (log/error "Error closing subscription channel: " (.getMessage e))))
    (reset! pubsub-listener nil)
    true))
