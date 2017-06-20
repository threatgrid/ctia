(ns ctia.flows.hooks.kafka
  (:require [clojure.tools.logging :as log]
            [ctia.flows.hook-protocol :refer [Hook]]
            [ctia.properties :refer [properties]]
            [cheshire.core :as json]
            [franzy.clients.producer.defaults :as pd]
            [franzy.clients.producer.protocols :as kafka]
            [franzy.clients.producer.client :as producer]
            [franzy.serialization.serializers :as serializers]))

(def production-key "data")

(defrecord KafkaPublisher [producer topic partition opts]
  Hook
  (init [_]
    :nothing)
  (destroy [_]
    (.close producer))
  (handle [_ event _]
    (kafka/send-async! producer topic partition production-key event opts)))

(defn new-publisher
  ([]
   (let [{:keys [enabled host port topic partition security truststore password]}
         (get-in @properties [:cita :hook :kafka])

         config (cond-> {:bootstrap.servers (str host ":" port)}
                  security (assoc :security.protocol (str/upper-case security))
                  truststore (assoc :ssl.truststore.location truststore)
                  password (assoc :ssl.truststore.password password))
         options (pd/make-default-producer-options)
         key-ser (serializers/string-serializer)
         val-ser (serializers/string-serializer)
         producer (producer/make-producer config key-ser val-ser options)]
     (when enabled
       (->KafkaPublisher producer topic partition options)))))
