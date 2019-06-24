(ns ctia.lib.kafka
  (:require [ctia.properties :refer [properties]]
            [onyx.kafka.helpers :as okh]
            [onyx.plugin.kafka :as opk])
  (:import kafka.admin.AdminUtils))

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
                             "group.id" "ctia"}
                            consumer-opts)]
    (okh/build-consumer kafka-config
                        (okh/byte-array-deserializer)
                        (okh/byte-array-deserializer))))

(defn decompress
  [v]
  (when v (String. v "UTF-8")))

(defn poll [consumer name f timeout]
  (loop []
    (let [records (.poll consumer timeout)]
      (doseq [record (.records records name)]
        (f (okh/consumer-record->message decompress
                                         record)))
      (.commitSync consumer)
      (recur))))

(defn subscribe [f timeout]
  (let [{:keys [name]}
        (get-in @properties [:ctia :hook :kafka :topic])
        consumer (build-consumer)
        consumer-thread (Thread. #(poll consumer name f timeout))]
    (.subscribe consumer [name])
    (.start consumer-thread)
    {:consumer consumer
     :consumer-thread consumer-thread}))

(defn stop-consumer
  [{:keys [consumer
           consumer-thread]}]
  (.stop consumer-thread))

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

(defn kafka-delete-topic [zk-addr
                          topic-name]
  (with-open [zk-utils (okh/make-zk-utils {:servers zk-addr} false)]
    (AdminUtils/deleteTopic zk-utils
                            topic-name)))

(defn delete-topic []
  (let [{:keys [address]}
        (get-in @properties [:ctia :hook :kafka :zk])
        {:keys [name
                num-partitions
                replication-factor]}
        (get-in @properties [:ctia :hook :kafka :topic])]
    (kafka-delete-topic address name)))
