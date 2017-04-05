(ns ctia.events-test
  (:require [ctia.events :as e]
            [ctia.lib.async :as la]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :as es-helpers]
            [ctim.events.obj-to-event :as o2e]
            [clojure.test :as t :refer :all]
            [clojure.core.async :refer [poll! chan tap <!!]]
            [schema.test :as st]))

(use-fixtures :once st/validate-schemas)

(use-fixtures :each (join-fixtures [helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
                                    helpers/fixture-ctia-fast]))

(deftest test-send-event
  "Tests the basic action of sending an event"
  (let [{b :chan-buf c :chan m :mult :as ec} (la/new-channel)
        output (chan)]
    (try
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
      (is (= 3 (-> (<!! output) :entity :data)))
      (finally
        (la/shutdown-channel 100 ec)))))

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
