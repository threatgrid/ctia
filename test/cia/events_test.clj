(ns cia.events-test
  (:require [cia.events :as c :refer :all]
            [clojure.test :as t :refer :all]
            [clojure.core.async :refer [poll!]]))

(defn drain [c]
  (loop [acc []]
    (if-let [x (poll! c)]
      (recur (conj acc x))
      acc)))

(deftest test-send-event
  "Tests the basic action of sending an event"
  (let [{b :chan-buf c :chan :as ec} (new-event-channel)]
    (send-create-event ec "tester" {} "TestModelType" {:data "test"})
    (is (= 1 (count b)))
    (send-create-event ec "tester" {} "TestModelType" {:data "test"})
    (is (= 2 (count b)))
    (is (thrown? AssertionError
                 (send-event ec {:http-params {}})))
    (is (= 2 (count b)))
    (send-event ec {:owner "tester" :http-params {}})
    (is (= 3 (count b)))))

