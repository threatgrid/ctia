(ns ctia.publish-test
  (:require [clojure.test :refer :all]
            [ctia.publish :as pub]
            [ctia.events :as e]
            [ctia.stores.redis.store :as redis]
            [schema.test :as st]
            ))

(deftest test-events
  (e/init!)
  (pub/init!)
  (let [results (atom [])]
    (pub/event-subscribe #(swap! results conj %))
    (e/send-create-event "tester" {} "TestModelType" {:data 1})
    (e/send-event {:owner "tester" :http-params {} :model {:data 2}})
    (e/send-event {:owner "tester" :http-params {} :model {:data 3}})
    (Thread/sleep 100)   ;; wait until the go loop is done
    (is (= [1 2 3] (map (comp :data :model) @results)))))
