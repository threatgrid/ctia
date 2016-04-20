(ns ctia.publish
  (:require [ctia.events :as e]
            [ctia.stores.redis.store :as redis]
            [clojure.core.async :as a  :refer [>!!]]
            [schema.core :as s :refer [=>]])
  (:import [clojure.core.async.impl.protocols Channel]))

(def publish-channel "A central channel for publishing" (e/new-event-channel))

(defn- async-listener
  "The function that will be subscribed to Redis, and send events to the publishing channel."
  [event]
  (>!! (:chan publish-channel) event))

(defn init!
  "Initializes publishing. Right now, this means Redis."
  []
  (when (and (redis/enabled?)
             (redis/set-listener-fn! async-listener))
    (e/register-listener redis/publish-fn)))

(s/defn event-subscribe :- (s/maybe Channel)
  "Registers a function to be called with all new events published through Redis.
   Returns a channel that can be closed to terminate the subscription loop,
   or nil if no subscription occurred (because Redis is disabled)."
  [f :- (=> s/Any e/Event)]
  (when (redis/enabled?)
    (letfn [(event-fn [[msg-type channel-name event]]
              (when (= "message" msg-type)
                (f event)))]
      (e/register-listener publish-channel
                           event-fn
                           (constantly true)
                           redis/close!))))
