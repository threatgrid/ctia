(ns ctia.events-test
  (:require [ctia.events :as e]
            [ctia.lib.async :as la]
            [ctia.test-helpers.core :as helpers]
            [ctim.events.obj-to-event :as o2e]
            [clojure.test :as t :refer :all]
            [clojure.core.async :refer [poll! chan tap <!!]]
            [schema.test :as st]))

(use-fixtures :once st/validate-schemas)

(use-fixtures :each (join-fixtures [helpers/fixture-properties:clean
                                    helpers/fixture-properties:atom-store
                                    helpers/fixture-ctia-fast]))

(deftest test-send-event
  "Tests the basic action of sending an event"
  (let [{b :chan-buf c :chan m :mult :as ec} (la/new-channel)
        output (chan)]
    (tap m output)
    (e/send-event ec (o2e/to-create-event
                      {:owner "tester"
                       :id "test-1"
                       :type :test
                       :data 1}))
    (e/send-event ec (o2e/to-create-event
                      {:owner "tester"
                       :id "test-2"
                       :type :test
                       :data 2}))
    (e/send-event ec (o2e/to-create-event
                      {:owner "tester"
                       :id "test-3"
                       :type :test
                       :data 3}))
    (is (= 1 (-> (<!! output) :entity :data)))
    (is (= 2 (-> (<!! output) :entity :data)))
    (is (= 3 (-> (<!! output) :entity :data)))))

(deftest test-central-events
  "Tests the basic action of sending an event to the central channel"
  (let [{b :chan-buf c :chan m :mult} @e/central-channel
        output (chan)]
    (tap m output)
    (e/send-event (o2e/to-create-event
                   {:owner "tester"
                    :id "test-1"
                    :type :test
                    :data 1}))
    (e/send-event (o2e/to-create-event
                   {:owner "teseter"
                    :id "test-2"
                    :type :test
                    :data 2}))
    (is (= 1 (-> (<!! output) :entity :data)))
    (is (= 2 (-> (<!! output) :entity :data)))
    (is (nil? (poll! output)))))

(deftest test-recents
  "Tests that the sliding window works, and is repeatable"
  (binding [la/*channel-buffer-size* 3]
    (e/shutdown!)
    (e/init!)
    (e/send-event (o2e/to-create-event
                   {:owner "tester"
                    :id "test-1"
                    :type :test
                    :data 1}))
    (e/send-event (o2e/to-create-event
                   {:owner "tester"
                    :id "test-2"
                    :type :test
                    :data 2}))
    (e/send-event (o2e/to-create-event
                   {:owner "tester"
                    :id "test-2"
                    :type :test
                    :data 3}))
    (Thread/sleep 100)
    (is (= [1 2 3] (map (comp :data :entity) (e/recent-events))))
    (e/send-event (o2e/to-create-event
                   {:owner "tester"
                    :id "test-4"
                    :type :test
                    :data 4}))
    (e/send-event (o2e/to-create-event
                   {:owner "tester"
                    :id "test-5"
                    :type :test
                    :data 5}))
    (e/send-event (o2e/to-create-event
                   {:owner "tester"
                    :id "test-6"
                    :type :test
                    :data 6}))
    (e/send-event (o2e/to-create-event
                   {:owner "tester"
                    :id "test-7"
                    :type :test
                    :data 7}))
    (Thread/sleep 100)
    (is (= [5 6 7] (map (comp :data :entity) (e/recent-events))))))
