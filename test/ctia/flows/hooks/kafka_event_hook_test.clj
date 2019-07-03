(ns ctia.flows.hooks.kafka-event-hook-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.string :as str]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.lib.kafka :as lk]
            [ctia.properties :refer [properties]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]
            [cheshire.core :refer [parse-string]]

            [ctia.test-helpers
             [core :as test-helpers :refer [post]]
             [es :as es-helpers]])
  (:import [java.util.concurrent CountDownLatch TimeUnit]
           [org.apache.kafka.clients.consumer ConsumerRebalanceListener]))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each
  (join-fixtures [test-helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store
                  test-helpers/fixture-properties:kafka-hook
                  test-helpers/fixture-properties:events-enabled
                  test-helpers/fixture-ctia
                  test-helpers/fixture-allow-all-auth]))

(deftest ^:integration test-events-topic
  (testing "Events are published to kafka topic"
    (let [results (atom [])
          finish-signal (CountDownLatch. 3)
          rebalance-signal (CountDownLatch. 1)
          kafka-props (get-in @properties [:ctia :hook :kafka])
          consumer-map
          (lk/subscribe
           kafka-props
           (fn test-events-kafka-topic-fn [ev]
             (let [v (:value ev)]
               (swap! results conj (parse-string v true))
               (.countDown finish-signal)))
           {:timeout 30000
            :rebalance-listener
            (reify ConsumerRebalanceListener
              (onPartitionsRevoked [_ _]
                true)
              (onPartitionsAssigned [_ _]
                (.countDown rebalance-signal)))})]

      (.await rebalance-signal 30 TimeUnit/SECONDS)

      (let [{{judgement-1-long-id :id} :parsed-body
             judgement-1-status :status
             :as judgement-1}
            (post "ctia/judgement"
                  :body {:observable {:value "1.2.3.4"
                                      :type "ip"}
                         :disposition 1
                         :source "source"
                         :tlp "green"
                         :priority 100
                         :severity "High"
                         :confidence "Low"
                         :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

            judgement-1-id
            (id/long-id->id judgement-1-long-id)

            {{judgement-2-long-id :id} :parsed-body
             judgement-2-status :status
             :as judgement-2}
            (post "ctia/judgement"
                  :body {:observable {:value "1.2.3.4"
                                      :type "ip"}
                         :disposition 2
                         :source "source"
                         :tlp "green"
                         :priority 100
                         :severity "High"
                         :confidence "Low"
                         :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

            judgement-2-id
            (id/long-id->id judgement-2-long-id)

            {{judgement-3-long-id :id} :parsed-body
             judgement-3-status :status
             :as judgement-3}
            (post "ctia/judgement"
                  :body {:observable {:value "1.2.3.4"
                                      :type "ip"}
                         :disposition 3
                         :source "source"
                         :tlp "green"
                         :priority 100
                         :severity "High"
                         :confidence "Low"
                         :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

            judgement-3-id
            (id/long-id->id judgement-3-long-id)]

        (is (= 201 judgement-1-status))
        (is (= 201 judgement-2-status))
        (is (= 201 judgement-3-status))

        (is (.await finish-signal 30 TimeUnit/SECONDS)
            "Unexpected timeout waiting for events")

        (lk/stop-consumer consumer-map)
        (lk/delete-topic kafka-props)

        (is (= [{:owner "Unknown"
                 :groups ["Administrators"]
                 :type "event"
                 :tlp "green"
                 :entity {:valid_time
                          {:start_time "2016-02-11T00:40:48.212Z"
                           :end_time "2525-01-01T00:00:00.000Z"}
                          :observable {:value "1.2.3.4" :type "ip"},
                          :type "judgement"
                          :source "source"
                          :tlp "green"
                          :schema_version schema-version
                          :disposition 1
                          :disposition_name "Clean"
                          :priority 100
                          :id (id/long-id judgement-1-id)
                          :severity "High"
                          :confidence "Low"
                          :owner "Unknown"
                          :groups ["Administrators"]}
                 :event_type "record-created"}
                {:owner "Unknown"
                 :groups ["Administrators"]
                 :type "event"
                 :tlp "green"
                 :entity {:valid_time
                          {:start_time "2016-02-11T00:40:48.212Z"
                           :end_time "2525-01-01T00:00:00.000Z"}
                          :observable {:value "1.2.3.4" :type "ip"},
                          :type "judgement"
                          :source "source"
                          :tlp "green"
                          :schema_version schema-version
                          :disposition 2
                          :disposition_name "Malicious"
                          :priority 100
                          :id (id/long-id judgement-2-id)
                          :severity "High"
                          :confidence "Low"
                          :owner "Unknown"
                          :groups ["Administrators"]}
                 :event_type "record-created"}
                {:owner "Unknown"
                 :groups ["Administrators"]
                 :type "event"
                 :tlp "green"
                 :entity {:valid_time
                          {:start_time "2016-02-11T00:40:48.212Z"
                           :end_time "2525-01-01T00:00:00.000Z"}
                          :observable {:value "1.2.3.4" :type "ip"},
                          :type "judgement"
                          :source "source"
                          :tlp "green"
                          :schema_version schema-version
                          :disposition 3
                          :disposition_name "Suspicious"
                          :priority 100
                          :id (id/long-id judgement-3-id)
                          :severity "High"
                          :confidence "Low"
                          :owner "Unknown"
                          :groups ["Administrators"]}
                 :event_type "record-created"}]
               (->> @results
                    (map #(dissoc % :timestamp :id :modified))
                    (map #(update % :entity dissoc :created :modified :timestamp)))))))))
