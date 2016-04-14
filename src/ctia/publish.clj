(ns ctia.publish
  (:require [ctia.events :as e]
            [ctia.stores.redis.store :as redis]
            [clojure.core.async :as a  :refer [>!!]]
            [schema.core :as s :refer [=>]]))

(def publish-channel "A central channel for publishing" (e/new-event-channel))

(defn- async-listener
  "The function that will be subscribed to Redis, and send events to the publishing channel."
  [event]
  (>!! (:chan publish-channel) event))

(defn init!
  "Initializes publishing. Right now, this means Redis."
  []
  (redis/set-listener-fn! async-listener)
  (e/register-listener redis/publish-fn))

(s/defn event-subscribe
  "Registers a function to be called with all new events published through Redis."
  [f :- (=> s/Any e/Event)]
  (letfn [(event-fn [[msg-type channel-name event]]
            (when (= "message" msg-type)
              (f event)))]
    (e/register-listener publish-channel
                         event-fn
                         (constantly true)
                         redis/close!)))
