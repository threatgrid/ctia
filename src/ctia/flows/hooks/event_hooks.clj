(ns ctia.flows.hooks.event-hooks
  (:require
   [clojure.tools.logging :as log]
   [ctia.flows.hook-protocol :refer [Hook]]
   [ctia.lib.redis :as lr]
   [ctia.lib.kafka :as lk]
   [ctia.flows.hooks-service.schemas :refer [HooksMap]]
   [ctia.schemas.services :refer [ConfigServiceFns]]
   [redismq.core :as rmq]
   [onyx.kafka.helpers :as okh]
   [cheshire.core :refer [generate-string]]
   [schema.core :as s]
   [schema-tools.core :as st]
   [amazonica.aws.s3 :as s3])
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

(defn string->length-and-stream
  ([s] (string->length-and-stream s "UTF-8"))
  ([^String s encoding]
   (let [bytes ^bytes (.getBytes s encoding)]
     [(count bytes) (java.io.ByteArrayInputStream. bytes)])))


(defrecord S3EventPublisher [s3-config]
  Hook
  (init [_]
    :nothing)
  (destroy [_]
    :nothing)
  (handle [_ event _]
    (let [[length stream] (string->length-and-stream (generate-string event))]
      (s3/put-object {:bucket-name (get s3-config :bucket)
                      :key (str (get s3-config :key-prefix) "/" (:id event))
                      :input-stream stream
                      :metadata {:content-length length}}))
    event))


(s/defn redis-event-publisher
  [get-in-config :- (st/get-in ConfigServiceFns [:get-in-config])]
  (let [{:keys [channel-name] :as redis-config}
        (get-in-config [:ctia :hook :redis])]
    (->RedisEventPublisher (lr/server-connection redis-config)
                           channel-name)))



(s/defn s3-event-publisher
  [get-in-config :- (st/get-in ConfigServiceFns [:get-in-config])]
  (let [s3-props (get-in-config [:ctia :hook :s3])]
    (->S3EventPublisher s3-props)))

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
         {s3? :enabled} :s3}
        (get-in-config [:ctia :hook])
        all-event-hooks
        (cond-> {}
          redis?   (assoc :redis (redis-event-publisher get-in-config))
          redismq? (assoc :redismq (redismq-publisher get-in-config))
          kafka?   (assoc :kafka (kafka-event-publisher get-in-config))
          s3?      (assoc :s3 (s3-event-publisher get-in-config)))]
    (update hooks-m :event into (vals all-event-hooks))))
