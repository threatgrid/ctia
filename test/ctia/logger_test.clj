(ns ctia.logger-test
  (:require [ctia.logging :refer :all]
            [ctia.events :as e]
            [ctia.test-helpers.core :as test-helpers]
            [clojure.test :as t :refer :all]
            [schema.test :as st]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(use-fixtures :once st/validate-schemas)
(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    test-helpers/fixture-ctia-fast]))

(deftest test-setup
  (let [{b :chan-buf c :chan m :mult :as ev} @e/central-channel]
    (log-channel ev)
    (e/send-create-event "tester" {} "TestModelType" {:data 1})
    (Thread/sleep 100))) ;; wait until the go loop is done

(deftest test-logged
  (let [{b :chan-buf c :chan m :mult :as ev} @e/central-channel
        sb (StringBuilder.)
        patched-log (fn [logger level throwable message]
                      (.append sb message)
                      (.append sb "\n"))]
    (with-redefs [log/log* patched-log]
      (log-channel ev)
      (e/send-create-event "tester" {} "TestModelType" {:data 1})
      (e/send-event {:owner "tester" :http-params {} :data 2})
      (Thread/sleep 100)   ;; wait until the go loop is done
      (let [scrubbed (-> (str sb)
                      (str/replace #"#inst \"[^\"]*\"" "#inst \"\"")
                      (str/replace #":id event[^,]*" ":id event"))
          expected "event: {:type CreatedModel, :owner tester, :timestamp #inst \"\", :http-params {}, :model-type TestModelType, :id event, :entity {:data 1}}\nevent: {:owner tester, :http-params {}, :data 2, :timestamp #inst \"\"}\n"]
      (is (= expected scrubbed))))))
