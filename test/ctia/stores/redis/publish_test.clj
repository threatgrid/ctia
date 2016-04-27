(ns ctia.stores.redis.publish-test
  (:require [clojure.test :refer :all]
            [ctia.events :as e]
            [ctia.store :as store]
            [ctia.test-helpers.core :as test-helpers])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(use-fixtures :once test-helpers/fixture-schema-validation)

(use-fixtures :each
  (join-fixtures [test-helpers/fixture-properties:clean
                  test-helpers/fixture-properties:redis-store
                  test-helpers/fixture-ctia-fast]))

(deftest ^:integration test-publish-connection
  (testing "Checking that Redis can be published to"
    (store/publish-event @store/events-store 42)))

(deftest ^:integration test-events
  (testing "Checking publications into redis get picked up by a subscription"
    (let [results (atom [])
          finish-signal (CountDownLatch. 3)
          sub-key (store/subscribe-to-events
                   @store/events-store
                   (fn publish-test-event-subscribe-fn [ev]
                     (swap! results conj ev)
                     (.countDown finish-signal)))]
      (e/send-create-event "tester" {} "TestModelType" {:data 1})
      (e/send-event {:owner "tester" :http-params {} :entity {:data 2}})
      (e/send-event {:owner "tester" :http-params {} :entity {:data 3}})
      (is (.await finish-signal 10 TimeUnit/SECONDS)
          "Unexpected timeout waiting for subscriptions")
      (is (= [1 2 3] (map (comp :data :entity) @results)))
      (store/unsubscribe-to-events @store/events-store sub-key))))
