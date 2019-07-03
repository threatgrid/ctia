(ns ctia.lib.kafka
  (:require
   [clojure.tools.logging :as log]
   [ctia.properties :refer [properties]]
   [onyx.kafka.helpers :as okh]
   [onyx.plugin.kafka :as opk])
  (:import kafka.admin.AdminUtils
           kafka.admin.AdminClient))

(defn decompress
  [v]
  (when v (String. v "UTF-8")))

(defn ssl-enabled? [{:keys [ssl]}]
  (boolean (:enabled ssl)))

(defn make-ssl-opts [{:keys [ssl]}]
  {"security.protocol" "ssl"
   "ssl.truststore.location"
   (get-in ssl [:truststore :location])
   "ssl.truststore.password"
   (get-in ssl [:truststore :password])
   "ssl.keystore.location"
   (get-in ssl [:keystore :location])
   "ssl.keystore.password"
   (get-in ssl [:keystore :password])
   "ssl.key.password"
   (get-in ssl [:key :password])})

(defn build-producer [kafka-props]
  (let [producer-opts {}
        {:keys [request-size compression]} kafka-props
        compression-type (:type compression)
        address (get-in kafka-props [:zk :address])
        brokers (opk/find-brokers {:kafka/zookeeper address})
        kafka-config (cond-> {"bootstrap.servers" brokers
                              "max.request.size" request-size}
                       (ssl-enabled? kafka-props) (into (make-ssl-opts kafka-props))
                       compression-type (assoc "compression.type"
                                               compression-type))]
    (okh/build-producer kafka-config
                        (okh/byte-array-serializer)
                        (okh/byte-array-serializer))))

(defn build-consumer [kafka-props]
  (let [{:keys [request-size]} kafka-props
        address (get-in kafka-props [:zk :address])
        brokers (opk/find-brokers {:kafka/zookeeper address})
        kafka-config (cond-> {"bootstrap.servers" brokers
                              "group.id" "ctia"}
                       (ssl-enabled? kafka-props) (into (make-ssl-opts kafka-props)))]
    (okh/build-consumer kafka-config
                        (okh/byte-array-deserializer)
                        (okh/byte-array-deserializer))))

(defn poll [consumer name f timeout]
  (loop []
    (let [records (.poll consumer timeout)]
      (doseq [record (.records records name)]
        (f (okh/consumer-record->message decompress
                                         record)))
      (.commitSync consumer)
      (recur))))

(defn subscribe
  "Given a handler function,
   create a producer and start polling the requested topic
   return a map with the KafkaConsumer and the wrapping thread"
  [kafka-props handler {:keys [timeout
                               rebalance-listener]}]
  (let [{:keys [name]}
        (:topic kafka-props)
        consumer (build-consumer kafka-props)
        consumer-thread (Thread. #(poll consumer
                                        name
                                        handler
                                        timeout))]
    (if rebalance-listener
      (.subscribe consumer [name] rebalance-listener)
      (.subscribe consumer [name]))

    (.start consumer-thread)
    {:consumer consumer
     :consumer-thread consumer-thread}))

(defn stop-consumer
  [{:keys [consumer
           consumer-thread]}]
  (.stop consumer-thread))

(defn create-topic [kafka-props]
  (let [address (get-in kafka-props [:zk :address])
        {:keys [name
                num-partitions
                replication-factor]}
        (:topic kafka-props)
        zk-utils (okh/make-zk-utils {:servers address} false)]
    (when-not (AdminUtils/topicExists zk-utils name)
      (okh/create-topic! address
                         name
                         num-partitions
                         replication-factor))))

(defn kafka-delete-topic [zk-addr topic-name]
  (with-open [zk-utils (okh/make-zk-utils {:servers zk-addr} false)]
    (AdminUtils/deleteTopic zk-utils
                            topic-name)))

(defn delete-topic [kafka-props]
  (let [address (get-in kafka-props [:zk :address])
        {:keys [name
                num-partitions
                replication-factor]}
        (:topic kafka-props)]
    (kafka-delete-topic address name)))
