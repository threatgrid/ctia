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

(defn ssl-enabled? []
  (boolean (get-in @properties
                   [:ctia :hook :kafka :ssl :enabled])))

(defn make-ssl-opts []
  (let [ssl-props (get-in @properties [:ctia :hook :kafka :ssl])]
    {"security.protocol" "ssl"
     "ssl.truststore.location"
     (get-in ssl-props [:truststore :location])
     "ssl.truststore.password"
     (get-in ssl-props [:truststore :password])
     "ssl.keystore.location"
     (get-in ssl-props [:keystore :location])
     "ssl.keystore.password"
     (get-in ssl-props [:keystore :password])
     "ssl.key.password"
     (get-in ssl-props [:key :password])}))

(defn build-producer [kafka-config]
  (let [producer-opts {}
        {:keys [request-size compression]} kafka-config
        compression-type (:type compression)
        {:keys [session-timeout
                connection-timeout
                operation-retry-timeout
                address] :as zk-config} (:zk kafka-config)
        brokers (opk/find-brokers {:kafka/zookeeper address})
        kafka-config (cond-> {"bootstrap.servers" brokers
                              "max.request.size" request-size}
                       (ssl-enabled?) (merge (make-ssl-opts))
                       compression-type (assoc "compression.type"
                                               compression-type))]
    (okh/build-producer kafka-config
                        (okh/byte-array-serializer)
                        (okh/byte-array-serializer))))

(defn build-consumer [kafka-config]
  (let [{:keys [request-size]} kafka-config
        {:keys [session-timeout
                connection-timeout
                operation-retry-timeout
                address] :as zk-config} (:zk kafka-config)
        brokers (opk/find-brokers {:kafka/zookeeper address})
        kafka-config (cond-> {"bootstrap.servers" brokers
                              "group.id" "ctia"}
                       (ssl-enabled?) (merge (make-ssl-opts)))]
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
  [kafka-config handler timeout]
  (let [{:keys [name]}
        (:topic kafka-config)
        consumer (build-consumer kafka-config)
        consumer-thread (Thread. #(poll consumer
                                        name
                                        handler
                                        timeout))]
    (.subscribe consumer [name])
    (.start consumer-thread)
    {:consumer consumer
     :consumer-thread consumer-thread}))

(defn stop-consumer
  [{:keys [consumer
           consumer-thread]}]
  (.stop consumer-thread))

(defn create-topic [kafka-config]
  (let [address (get-in kafka-config [:zk :address])
        {:keys [name
                num-partitions
                replication-factor]}
        (:topic kafka-config)
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

(defn delete-topic [kafka-config]
  (let [address (get-in kafka-config [:zk :address])
        {:keys [name
                num-partitions
                replication-factor]}
        (:topic kafka-config)]
    (kafka-delete-topic address name)))
