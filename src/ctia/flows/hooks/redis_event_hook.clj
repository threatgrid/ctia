(ns ctia.flows.hooks.redis-event-hook
  (:require [ctia.flows.hook-protocol :refer [Hook]]
            [ctia.lib.redis :as lr]
            [ctia.properties :refer [properties]]
            [ctia.properties.getters :as pg]))

(defrecord RedisEventPublisher [conn publish-channel-name]
  Hook
  (init [_]
    :nothing)
  (destroy [_]
    :nothing)
  (handle [_ _ object _]
    (lr/publish conn
                publish-channel-name
                object)))

(defn create-redis-event-publisher []
  (let [{:keys [channel-name timeout-ms] :as redis-config} (get-in @properties [:ctia :hook :redis])
        [host port] (pg/parse-host-port redis-config)]
    (->RedisEventPublisher (lr/server-connection host port timeout-ms)
                           channel-name)))
