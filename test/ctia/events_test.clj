(ns ctia.events-test
  (:require [ctia.events :as c :refer :all]
            [ctia.lib.async :as la]
            [ctia.test-helpers.core :as helpers]
            [clojure.test :as t :refer :all]
            [clojure.core.async :refer [poll! chan tap <!!]]
            [schema.test :as st]))

(use-fixtures :once st/validate-schemas)

(use-fixtures :each (join-fixtures [helpers/fixture-properties:clean
                                    helpers/fixture-ctia-fast]))

(deftest test-send-event
  "Tests the basic action of sending an event"
  (let [{b :chan-buf c :chan m :mult :as ec} (la/new-event-channel)
        output (chan)]
    (tap m output)
    (send-create-event ec "tester" {} "TestModelType" {:data 1})
    (send-create-event ec "tester" {} "TestModelType" {:data 2})
    (is (thrown? AssertionError
                 (send-event ec {:http-params {}})))
    (send-event ec {:owner "tester" :http-params {} :data 3})
    (is (= 1 (-> (<!! output) :model :data)))
    (is (= 2 (-> (<!! output) :model :data)))
    (is (= 3 (-> (<!! output) :data)))))

(deftest test-central-events
  "Tests the basic action of sending an event to the central channel"
  (let [{b :chan-buf c :chan m :mult} @central-channel
        output (chan)]
    (tap m output)
    (send-create-event "tester" {} "TestModelType" {:data 1})
    (send-create-event "tester" {} "TestModelType" {:data 2})
    (send-event {:owner "tester" :http-params {} :data 3})
    (is (= 1 (-> (<!! output) :model :data)))
    (is (= 2 (-> (<!! output) :model :data)))
    (is (= 3 (-> (<!! output) :data)))
    (is (nil? (poll! output)))))

(deftest test-updated-model
  "Tests the update-model function"
  (let [{b :chan-buf c :chan m :mult} @central-channel
        output (chan)]
    (tap m output)
    (send-updated-model "tester" {"User-Agent" "clojure"} [[:f1 "delete" {"x" "y"}][:f2 "assert" {"x" "y"}]])
    (is (= :f1 (-> (<!! output) :fields ffirst)))))

(deftest test-deleted-model
  "Tests the deleted-model function"
  (let [{b :chan-buf c :chan m :mult} @central-channel
        output (chan)]
    (tap m output)
    (send-deleted-model "tester" {"User-Agent" "clojure"} "42")
    (is (= "42" (-> (<!! output) :id)))))

(deftest test-verdict-change
  "Tests the verdict change function"
  (let [{b :chan-buf c :chan m :mult} @central-channel
        output (chan)]
    (tap m output)
    (send-verdict-change "tester" {"User-Agent" "clojure"} "7" {:type "verdict"
                                                                :disposition 2})
    (let [v (<!! output)]
      (is (= "7" (-> v :judgement_id)))
      (is (= 2 (-> v :verdict :disposition))))))

(deftest test-recents
  "Tests that the sliding window works, and is repeatable"
  (binding [la/*event-buffer-size* 3]
    (c/shutdown!)
    (c/init!)
    (send-create-event "tester" {} "TestModelType" {:id "1"})
    (send-create-event "tester" {} "TestModelType" {:id "2"})
    (send-create-event "tester" {} "TestModelType" {:id "3"})
    (Thread/sleep 100)
    (is (= ["1" "2" "3"] (map (comp :id :model) (recent-events))))
    (send-create-event "tester" {} "TestModelType" {:id "4"})
    (send-create-event "tester" {} "TestModelType" {:id "5"})
    (send-create-event "tester" {} "TestModelType" {:id "6"})
    (send-create-event "tester" {} "TestModelType" {:id "7"})
    (Thread/sleep 100)
    (is (= ["5" "6" "7"] (map (comp :id :model) (recent-events))))))
