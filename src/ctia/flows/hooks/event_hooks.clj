(ns ctia.flows.hooks.event-hooks
  (:require
   [clojure.tools.logging :as log]
   [ctia.flows.hook-protocol :refer [Hook]]
   [ctia.lib.redis :as lr]
   [ctia.lib.firehose :as lf]
   [ctia.lib.kafka :as lk]
   [ctia.flows.hooks-service.schemas :refer [HooksMap]]
   [ctia.schemas.services :refer [ConfigServiceFns]]
   [redismq.core :as rmq]
   [onyx.kafka.helpers :as okh]
   [cheshire.core :refer [generate-string]]
   [schema.core :as s]
   [schema-tools.core :as st])
  (:import
   [software.amazon.awssdk.services.firehose FirehoseClient FirehoseClientBuilder]
   [org.apache.kafka.clients.producer KafkaProducer]))

(defn event->bytes
  "Given a map, generate the json string and get the bytes."
  [event]
  (.getBytes (generate-string event)))

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
                    (event->bytes event))
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

(defrecord FirehoseEventPublisher [^FirehoseClientBuilder client-builder stream-name]
  Hook
  (init [_]
    :nothing)
  (destroy [_]
    :nothing)
  (handle [_ event _]
    (with-open [^FirehoseClient client (lf/build-client client-builder)]
      (try
        (lf/put-record client stream-name (event->bytes event))
        (catch Exception e
          (log/error e "Unable to push an event to Firehose"))))
    event))

(s/defn firehose-event-publisher
  [get-in-config :- (st/get-in ConfigServiceFns [:get-in-config])]
  (let [{:keys [stream-name]} (get-in-config [:ctia :hook :firehose])
        aws-config (get-in-config [:ctia :aws])]
    (->FirehoseEventPublisher (lf/create-client-builder aws-config)
                              stream-name)))

(s/defn redis-event-publisher
  [get-in-config :- (st/get-in ConfigServiceFns [:get-in-config])]
  (let [{:keys [channel-name] :as redis-config}
        (get-in-config [:ctia :hook :redis])]
    (->RedisEventPublisher (lr/server-connection redis-config)
                           channel-name)))

(s/defn kafka-event-publisher
  [get-in-config :- (st/get-in ConfigServiceFns [:get-in-config])]
  (let [kafka-props (get-in-config [:ctia :hook :kafka])]

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

(s/defn redismq-publisher
  [get-in-config :- (st/get-in ConfigServiceFns [:get-in-config])]
  (let [{:keys [queue-name host port timeout-ms max-depth enabled
                password ssl]
         :as config
         :or {queue-name "ctim-event-queue"
              host "localhost"
              port 6379}}
        (get-in-config [:ctia :hook :redismq])
        conn-spec (lr/redis-conf->conn-spec config)]
    (->RedisMQPublisher (rmq/make-queue queue-name
                                        conn-spec
                                        {:max-depth max-depth}))))

(s/defn register-hooks :- HooksMap
  "Append hooks from ctia.hook.* configuration to
  first argument."
  [hooks-m :- HooksMap
   get-in-config :- (st/get-in ConfigServiceFns [:get-in-config])]
  (let [{{redis? :enabled} :redis
         {redismq? :enabled} :redismq
         {kafka? :enabled} :kafka
         {firehose? :enabled} :firehose}
        (get-in-config [:ctia :hook])
        all-event-hooks
        (cond-> {}
          redis?   (assoc :redis (redis-event-publisher get-in-config))
          redismq? (assoc :redismq (redismq-publisher get-in-config))
          firehose? (assoc :firehose (firehose-event-publisher get-in-config))
          kafka?   (assoc :kafka (kafka-event-publisher get-in-config)))]
    (update hooks-m :event into (vals all-event-hooks))))
