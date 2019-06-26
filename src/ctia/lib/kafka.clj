(ns ctia.lib.kafka
  (:require
   [clojure.tools.logging :as log]
   [ctia.properties :refer [properties]]
   [onyx.kafka.helpers :as okh]
   [onyx.plugin.kafka :as opk])
  (:import kafka.admin.AdminUtils))

(defn decompress
  [v]
  (when v (String. v "UTF-8")))

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

(defn build-producer []
  (let [ssl-opts (make-ssl-opts)
        producer-opts {}
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
                            ssl-opts)]
    (okh/build-producer kafka-config
                        (okh/byte-array-serializer)
                        (okh/byte-array-serializer))))

(defn build-consumer []
  (let [ssl-opts (make-ssl-opts)
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
                            ssl-opts)]
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
  (let [consumer (build-consumer)
        {:keys [address]}
        (get-in @properties [:ctia :hook :kafka :zk])
        {:keys [name
                num-partitions
                replication-factor]}
        (get-in @properties [:ctia :hook :kafka :topic])
        current-topics (-> (.listTopics consumer)
                           keys
                           vector)]
    (log/debug (str "current topics: " (pr-str current-topics)))
    (when-not (contains? current-topics name)
      (okh/create-topic! address
                         name
                         num-partitions
                         replication-factor))))

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
