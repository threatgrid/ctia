(ns ctia.flows.hooks.event-hooks
  (:require
   [clojure.tools.logging :as log]
   [ctia.events :as events]
   [ctia.events-service :as events-svc]
   [ctia.flows.hook-protocol :refer [Hook]]
   [ctia.lib.redis :as lr]
   [ctia.lib.kafka :as lk]
   [ctia.properties :refer [properties]]
   [ctia.entity.event.schemas :refer [CreateEventType
                                      DeleteEventType]]
   [redismq.core :as rmq]
   [onyx.kafka.helpers :as okh]
   [onyx.plugin.kafka :as opk]
   [cheshire.core :refer [generate-string]]
   [schema.core :as s])
  (:import [org.apache.kafka.clients.producer KafkaProducer]))

(defrecord KafkaEventPublisher [^KafkaProducer producer kafka-config]
  Hook
  (init [_]
    :nothing)
  (destroy [_]
    (log/warn "shutting down Kafka producer")
    (.close producer))
  (handle [_ event _]
    (okh/send-sync! producer
                    (get-in kafka-config [:topic :name])
                    nil
                    (.getBytes ^String (:id event))
                    (.getBytes (generate-string event)))
    event))

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
  (let [{:keys [channel-name] :as redis-config}
        (get-in @(get-global-properties) [:ctia :hook :redis])]
    (->RedisEventPublisher (lr/server-connection redis-config)
                           channel-name)))

(defn kafka-event-publisher []
  (let [kafka-props (get-in @(get-global-properties)
                            [:ctia :hook :kafka])]

    (log/warn "Ensure Kafka topic creation")
    (try
      (lk/create-topic kafka-props)
      (catch org.apache.kafka.common.errors.TopicExistsException e
        (log/info "Kafka topic already exists")))

    (->KafkaEventPublisher
     (lk/build-producer kafka-props)
     kafka-props)))

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
  (let [{:keys [queue-name host port timeout-ms max-depth enabled
                password ssl]
         :as config
         :or {queue-name "ctim-event-queue"
              host "localhost"
              port 6379}}
        (get-in @(get-global-properties) [:ctia :hook :redismq])
        conn-spec (lr/redis-conf->conn-spec config)]
    (->RedisMQPublisher (rmq/make-queue queue-name
                                        conn-spec
                                        {:max-depth max-depth}))))

(defrecord ChannelEventPublisher []
  Hook
  (init [_]
    (assert (some? @events-svc/global-events-service)
            "Events service was not setup"))
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
         {redismq? :enabled} :redismq
         {kafka? :enabled} :kafka}
        (get-in @(get-global-properties) [:ctia :hook])
        all-event-hooks
        (cond-> {}
          redis?   (assoc :redis (redis-event-publisher))
          redismq? (assoc :redismq (redismq-publisher))
          kafka?   (assoc :kafka (kafka-event-publisher)))]
    (update hooks-m :event concat (vals all-event-hooks))))
