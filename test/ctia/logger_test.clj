(ns ctia.logger-test
  (:require [ctia.test-helpers
             [core :as test-helpers]
             [es :as es-helpers]]
            [ctia.entity.event.obj-to-event :as o2e]
            [clojure.test :refer [deftest is use-fixtures join-fixtures]]
            [schema.test :as st]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(use-fixtures :once (join-fixtures [es-helpers/fixture-properties:es-store
                                    test-helpers/fixture-properties:events-logging
                                    test-helpers/fixture-ctia-fast
                                    st/validate-schemas]))

(deftest test-logged
  (let [app (test-helpers/get-current-app)
        {:keys [send-event]} (test-helpers/get-service-map app :EventsService)
        
        sb (StringBuilder.)
        patched-log (fn [logger
                         level
                         throwable
                         message]
                      (.append sb message)
                      (.append sb "\n"))]
    (with-redefs [log/log* patched-log]
      (send-event (o2e/to-create-event
                     {:owner "tester"
                      :groups ["foo"]
                      :id "test-1"
                      :type :test
                      :tlp "green"
                      :data 1}
                     "test-1"))
      (send-event (o2e/to-create-event
                     {:owner "tester"
                      :groups ["foo"]
                      :id "test-2"
                      :type :test
                      :tlp "green"
                      :data 2}
                     "test-2"))
      (Thread/sleep 100)   ;; wait until the go loop is done
      (let [scrubbed (-> (str sb)
                         (str/replace #"#inst \"[^\"]*\"" "#inst \"\"")
                         (str/replace #":id event[^,]*" ":id event")
                         (str/replace #"Lifecycle worker completed :boot lifecycle task; awaiting next task.\s"
                                      ""))
            expected
            "event: {:owner tester, :groups [foo], :entity {:owner tester, :groups [foo], :id test-1, :type :test, :tlp green, :data 1}, :timestamp #inst \"\", :id test-1, :type event, :tlp green, :event_type :record-created}\nevent: {:owner tester, :groups [foo], :entity {:owner tester, :groups [foo], :id test-2, :type :test, :tlp green, :data 2}, :timestamp #inst \"\", :id test-2, :type event, :tlp green, :event_type :record-created}\n"]
        (is (= expected scrubbed))))))
