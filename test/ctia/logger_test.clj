(ns ctia.logger-test
  (:require [ctia.test-helpers.core :as test-helpers]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.entity.event.obj-to-event :as o2e]
            [ctia.logging-core :refer [logging-prefix]]
            [clojure.test :refer [deftest is use-fixtures]]
            [schema.test :as st]
            [clojure.tools.logging :as log]))

(use-fixtures :each
  es-helpers/fixture-properties:es-store
  test-helpers/fixture-properties:events-logging
  test-helpers/fixture-ctia-fast
  st/validate-schemas)

(deftest test-logged
  (let [app (test-helpers/get-current-app)
        {:keys [send-event]} (test-helpers/get-service-map app :EventsService)
        events-atom (atom [])
        patched-log (fn [_logger
                         _level
                         _throwable
                         ^String message]
                      (when (.startsWith message logging-prefix)
                        ;; grab the edn after the logging-prefix
                        (swap! events-atom conj (read-string (subs message (count logging-prefix))))))]
    (with-redefs [log/log* patched-log]
      (send-event (o2e/to-create-event
                     {:owner "tester"
                      :groups ["foo"]
                      :id "test-1"
                      :type :test
                      :tlp "green"
                      :data 1}
                     "test-1"
                     "tester"))
      (send-event (o2e/to-create-event
                     {:owner "tester"
                      :groups ["foo"]
                      :id "test-2"
                      :type :test
                      :tlp "green"
                      :data 2}
                     "test-2"
                     "tester"))
      (Thread/sleep 100)   ;; wait until the go loop is done
      (is (= '[{:owner tester,
                :groups [foo],
                :entity {:owner tester,
                         :groups [foo],
                         :id test-1,
                         :type :test,
                         :tlp green,
                         :data 1},
                :id test-1,
                :type event,
                :tlp green,
                :event_type :record-created}
               {:owner tester,
                :groups [foo], 
                :entity {:owner tester, 
                         :groups [foo], 
                         :id test-2, 
                         :type :test, 
                         :tlp green, 
                         :data 2}, 
                :id test-2,
                :type event, 
                :tlp green,
                :event_type :record-created}]
             (map #(dissoc % :timestamp) @events-atom))))))
