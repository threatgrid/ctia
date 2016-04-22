(ns ctia.subscribe
  (:require [ctia.events.schemas :as es]
            [ctia.lib.async :as la]
            [ctia.stores.redis.store :as redis]
            [clojure.core.async :as a :refer [>!!]]
            [schema.core :as s :refer [=>]])
  (:import [clojure.core.async.impl.protocols Channel]))

(def subscription-channel "A central channel for receiving the subscription" (la/new-event-channel))

(defn- async-listener
  "The function that will be subscribed to Redis, and send events to the publishing channel."
  [event]
  (>!! (:chan subscription-channel) event))

(defn init! []
  (redis/set-listener-fn! async-listener))

(s/defn event-subscribe :- (s/maybe Channel)
  "Registers a function to be called with all new events published through Redis.
   Returns a channel that can be closed to terminate the subscription loop,
   or nil if no subscription occurred (because Redis is disabled)."
  [f :- (=> s/Any es/Event)]
  (when (redis/enabled?)
    (redis/set-listener-fn! async-listener)  ;; Call in case init! didn't happen. This is guarded.
    (letfn [(event-fn [[msg-type channel-name event]]
              (when (= "message" msg-type)
                (f event)))]
      (la/register-listener subscription-channel
                            event-fn
                            (constantly true)
                            redis/close!))))

