(ns ctia.lib.redis-test
  (:require [clojure.core.async :as a]
            [clojure.test
             :refer [join-fixtures deftest is testing use-fixtures]]
            [ctia.lib.redis :as lr]
            [ctia.properties :refer [properties]]
            [ctia.properties.getters :as pget]
            [ctia.test-helpers.core :as test-helpers])
  (:import [java.util.concurrent CountDownLatch TimeUnit]
           java.util.UUID))

(use-fixtures :each (join-fixtures
                     [test-helpers/fixture-properties:clean
                      test-helpers/fixture-properties:redis-store
                      test-helpers/fixture-ctia-fast]))

(deftest ^:integration test-redis-pubsub-works
  (testing "That we can connect to redis and do pub/sub"
    (let [host-port (pget/redis-host-port* @properties)
          server-connection (lr/server-connection
                             host-port)
          event-channel-name (str (UUID/randomUUID))
          results (atom [])
          finish-signal (CountDownLatch. 4)
          listener (lr/subscribe host-port
                                 event-channel-name
                                 (fn redis-test-listener-fn [i]
                                   (swap! results conj i)
                                   (.countDown finish-signal)))]
      (a/thread (lr/publish host-port event-channel-name {:message 1}))
      (a/thread (lr/publish host-port event-channel-name {:message 2}))
      (a/thread (lr/publish host-port event-channel-name {:message 3}))
      (is (.await finish-signal 10 TimeUnit/SECONDS)
          "Timeout waiting for redis to publish")
      (is (= #{["subscribe" event-channel-name 1]
               ["message"   event-channel-name {:message 1}]
               ["message"   event-channel-name {:message 2}]
               ["message"   event-channel-name {:message 3}]}
             (set @results))))))
