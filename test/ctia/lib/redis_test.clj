(ns ctia.lib.redis-test
  (:require [clojure.core.async :as a]
            [clojure.test
             :refer [join-fixtures deftest is testing use-fixtures]]
            [ctia.lib.redis :as lr]
            [ctia.properties :refer [properties]]
            [ctia.test-helpers.core :as test-helpers])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(use-fixtures :each (join-fixtures
                     [test-helpers/fixture-properties:clean
                      test-helpers/fixture-properties:atom-store
                      test-helpers/fixture-ctia-fast]))

(deftest ^:integration test-redis-pubsub-works
  (testing "That we can connect to redis and do pub/sub"
    (let [{:keys [timeout-ms host port] :as redis-config} (get-in @properties [:ctia :hook :redis])
          server-connection (lr/server-connection host port timeout-ms)
          event-channel-name (str (gensym "events"))
          results (atom [])
          finish-signal (CountDownLatch. 4)
          listener (lr/subscribe server-connection
                                 event-channel-name
                                 (fn redis-test-listener-fn [i]
                                   (swap! results conj i)
                                   (.countDown finish-signal)))]
      (a/thread (lr/publish server-connection event-channel-name {:message 1}))
      (a/thread (lr/publish server-connection event-channel-name {:message 2}))
      (a/thread (lr/publish server-connection event-channel-name {:message 3}))
      (is (.await finish-signal 10 TimeUnit/SECONDS)
          "Timeout waiting for redis to publish")
      (is (= #{["subscribe" event-channel-name 1]
               ["message"   event-channel-name {:message 1}]
               ["message"   event-channel-name {:message 2}]
               ["message"   event-channel-name {:message 3}]}
             (set @results)))
      (lr/close-listener listener))))
