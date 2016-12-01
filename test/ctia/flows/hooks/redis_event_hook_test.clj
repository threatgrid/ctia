(ns ctia.flows.hooks.redis-event-hook-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.string :as str]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.lib.redis :as lr]
            [ctia.properties :refer [properties]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [core :as test-helpers :refer [post]]
             [es :as es-helpers]])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
                                    test-helpers/fixture-properties:redis-hook
                                    test-helpers/fixture-properties:events-enabled
                                    test-helpers/fixture-ctia
                                    test-helpers/fixture-allow-all-auth]))

(deftest ^:integration test-events-pubsub
  (testing "Events are published to redis"
    (let [results (atom [])
          finish-signal (CountDownLatch. 3)
          {:keys [timeout-ms channel-name host port] :as redis-config} (get-in @properties [:ctia :hook :redis])
          listener (lr/subscribe-to-messages (lr/server-connection host port timeout-ms)
                                             channel-name
                                             (fn test-events-pubsub-fn [ev]
                                               (swap! results conj ev)
                                               (.countDown finish-signal)))]
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

        (is (.await finish-signal 10 TimeUnit/SECONDS)
            "Unexpected timeout waiting for subscriptions")
        (is (= [{:owner "Unknown"
                 :entity {:valid_time
                          {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2525-01-01T00:00:00.000-00:00"}
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
                          :owner "Unknown"}
                 :type "CreatedModel"}
                {:owner "Unknown"
                 :entity {:valid_time
                          {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2525-01-01T00:00:00.000-00:00"}
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
                          :owner "Unknown"}
                 :type "CreatedModel"}
                {:owner "Unknown"
                 :entity {:valid_time
                          {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2525-01-01T00:00:00.000-00:00"}
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
                          :owner "Unknown"}
                 :type "CreatedModel"}]
               (->> @results
                    (map #(dissoc % :timestamp :http-params :id))
                    (map #(update % :entity dissoc :created)))))

        (testing "variable event fields have correct type"
          (doseq [event @results]
            (is (str/starts-with? (:id event) "event-"))
            (is (instance? java.util.Date (:timestamp event))))))

      (lr/close-listener listener))))
