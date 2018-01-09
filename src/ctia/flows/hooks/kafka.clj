(ns ctia.flows.hooks.kafka
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [ctia.flows.hook-protocol :refer [Hook]]
            [ctia.properties :refer [properties]]
            [cheshire.core :as json]
            [franzy.clients.producer.defaults :as pd]
            [franzy.clients.producer.protocols :as kafka]
            [franzy.clients.producer.client :as producer]
            [franzy.serialization.serializers :as serializers]
            [franzy.admin.zookeeper.defaults :as zk-defaults]
            [franzy.admin.zookeeper.client :as admin-client]
            [franzy.admin.cluster :as cluster]
            [clients.core :as c]))

(def production-key "data")

(defn zookeeper-brokers
  [servers]
  (let [client-config (merge
                       (zk-defaults/zk-client-defaults)
                       {:servers servers})
        zkutils (admin-client/make-zk-utils client-config false)
        brokers (cluster/all-brokers zkutils)]
    (map (fn [{{{:keys [host port]} :plaintext} :endpoints}]
           (str host ":" port))
         brokers)))

(defn get-servers
  [zk-servers kafka-servers]
  (if kafka-servers
    (str/split kafka-servers #",")
    (zookeeper-brokers zk-servers)))

(defrecord KafkaPublisher [producer topic partition opts]
  Hook
  (init [_]
    :nothing)
  (destroy [_]
    (.flush! producer)
    (.close producer))
  (handle [_ event _]
    (let [event-json (json/encode event)]
      (future
        (c/retry-exp
         "send to Kafka"
         (kafka/send-sync! producer topic partition production-key event-json opts))))))

(defn new-publisher
  ([]
   (let [{:keys [enabled host-ports topic partition security truststore keystore password]}
         (get-in @properties [:ctia :hook :kafka])
         zk (get-in @properties [:ctia :hook :kafka :zookeeper :host-ports])]
     (when enabled
       (log/info "Configuring Kafka publishing")
       (let [config (cond-> {:bootstrap.servers (get-servers zk host-ports)}
                            security (assoc :security.protocol (str/upper-case security))
                            truststore (assoc :ssl.truststore.location truststore)
                            keystore (assoc :ssl.keystore.location keystore)
                            password (assoc :ssl.truststore.password password))
             options (pd/make-default-producer-options)
             key-ser (serializers/string-serializer)
             val-ser (serializers/string-serializer)
             producer (producer/make-producer config key-ser val-ser options)]
         (->KafkaPublisher producer topic partition options))))))
