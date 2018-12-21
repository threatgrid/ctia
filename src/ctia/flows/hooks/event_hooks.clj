(ns ctia.flows.hooks.event-hooks
  (:require
   [clojure.tools.logging :as log]
   [ctia.events :as events]
   [ctia.flows.hook-protocol :refer [Hook]]
   [ctia.lib.redis :as lr]
   [ctia.properties :refer [properties]]
   [ctia.entity.event.schemas :refer [CreateEventType
                                      DeleteEventType]]
   [redismq.core :as rmq]
   [schema.core :as s]))

(defrecord RedisEventPublisher [conn publish-channel-name]
  Hook
  (init [_]
    :nothing)
  (destroy [_]
    :nothing)
  (handle [_ event _]
    (try
      (lr/publish conn
                  publish-channel-name
                  event)
      (catch Exception e
        (log/error e "Unable to push an event to Redis")))
    event))

(defn redis-event-publisher []
  (let [{:keys [channel-name timeout-ms host port] :as redis-config}
        (get-in @properties [:ctia :hook :redis])]
    (->RedisEventPublisher (lr/server-connection host port timeout-ms)
                           channel-name)))

(defrecord RedisMQPublisher [queue]
  Hook
  (init [self]
    :nothing)
  (destroy [_]
    :nothing)
  (handle [self event _]
    (try
      (rmq/enqueue (get self :queue) event)
      (catch Exception e
        (log/error e "Unable to push an event to Redis")))
    event))

(defn redismq-publisher []
  (let [{:keys [queue-name host port timeout-ms max-depth enabled]
         :as config
         :or {queue-name "ctim-event-queue"
              host "localhost"
              port 6379}}
        (get-in @properties [:ctia :hook :redismq])]
    (->RedisMQPublisher (rmq/make-queue queue-name
                                        {:host host
                                         :port port
                                         :timeout-ms timeout-ms}
                                        {:max-depth max-depth}))))

(defrecord ChannelEventPublisher []
  Hook
  (init [_]
    (assert (some? @events/central-channel)
            "Events central-channel was not setup"))
  (destroy [_]
    :nothing)
  (handle [_ event _]
    (try
      (events/send-event event)
      (catch Exception e
        (log/error e "Unable to push an event to Redis")))
    event))

(defn- judgement?
  [{{t :type} :entity :as _event_}]
  (= "judgement" t))

(defn- create-event?
  [{type :type :as _event_}]
  (= type CreateEventType))

(defn- delete-event?
  [{type :type :as _event_}]
  (= type DeleteEventType))

(s/defn register-hooks :- {s/Keyword [(s/protocol Hook)]}
  [hooks-m :- {s/Keyword [(s/protocol Hook)]}]
  (let [{{redis? :enabled} :redis
         {redismq? :enabled} :redismq}
        (get-in @properties [:ctia :hook])]
    (cond-> hooks-m
      redis?   (update :event #(conj % (redis-event-publisher)))
      redismq? (update :event #(conj % (redismq-publisher))))))
