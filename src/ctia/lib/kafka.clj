(ns ctia.lib.kafka
  (:require
   [ctia.properties :refer [properties]]
   [onyx.plugin.kafka :as opk]
   [onyx.kafka.helpers :as okh]))

(defn build-producer []
  (let [producer-opts {}
        {:keys [request-size]}
        (get-in @properties [:ctia :hook :kafka])
        {:keys [session-timeout
                connection-timeout
                operation-retry-timeout
                address] :as zk-config}
        (get-in @properties [:ctia :hook :kafka :zk])
        brokers (opk/find-brokers {:kafka/zookeeper address})
        kafka-config (merge {"bootstrap.servers" brokers
                             "max.request.size" request-size}
                            producer-opts)
        {:keys [name
                num-partitions
                replication-factor] :as kafka-topic-config}
        (get-in @properties [:ctia :hook :kafka :topic])]

    (okh/build-producer kafka-config
                        (okh/byte-array-serializer)
                        (okh/byte-array-serializer))))

(defn build-consumer []
  (let [consumer-opts {}
        {:keys [request-size]}
        (get-in @properties [:ctia :hook :kafka])
        {:keys [session-timeout
                connection-timeout
                operation-retry-timeout
                address] :as zk-config}
        (get-in @properties [:ctia :hook :kafka :zk])
        brokers (opk/find-brokers {:kafka/zookeeper address})
        kafka-config (merge {"bootstrap.servers" brokers
                             "max.request.size" request-size}
                            consumer-opts)
        {:keys [name
                num-partitions
                replication-factor] :as kafka-topic-config}
        (get-in @properties [:ctia :hook :kafka :topic])]
    (okh/build-consumer kafka-config
                        (okh/byte-array-serializer)
                        (okh/byte-array-serializer))))

(defn create-topic []
  (let [{:keys [address]}
        (get-in @properties [:ctia :hook :kafka :zk])
        {:keys [name
                num-partitions
                replication-factor]}
        (get-in @properties [:ctia :hook :kafka :topic])]
    (okh/create-topic! address
                       name
                       num-partitions
                       replication-factor)))
