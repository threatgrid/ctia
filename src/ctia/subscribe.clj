(ns ctia.subscribe
  (:require [ctia.events.schemas :as es]
            [ctia.lib.async :as la]
            [ctia.stores.redis.store :as redis]
            [clojure.core.async :as a :refer [>!!]]
            [schema.core :as s :refer [=>]])
  (:import [clojure.core.async.impl.protocols Channel]))

(defonce subscription-channel
  ^{:doc "A central channel for receiving the subscription"}
  (atom nil))

(defn- async-listener
  "The function that will be subscribed to Redis, and send events to the publishing channel."
  [event]
  (>!! (:chan @subscription-channel) event))

(defn init! []
  (reset! subscription-channel (la/new-event-channel))
  (redis/set-listener-fn! async-listener))

(defn shutdown! []
  (a/close! subscription-channel))

(s/defn event-subscribe :- (s/maybe Channel)
  "Registers a function to be called with all new events published through Redis.
   Returns a channel that can be closed to terminate the subscription loop,
   or nil if no subscription occurred (because Redis is disabled)."
  [inner-event-fn :- (=> s/Any es/Event)]
  {:pre [(redis/enabled?)
         (some? @subscription-channel)]}
  (la/register-listener @subscription-channel
                        (fn event-fn [[msg-type channel-name event]]
                          (when (= "message" msg-type)
                            (inner-event-fn event)))
                        (constantly true)
                        redis/shutdown!))
