(ns ctia.logger-test
  (:require [ctia.logging :as clog]
            [ctia.events :as e]
            [ctia.lib.async :as la]
            [clojure.test :as t :refer :all]
            [schema.test :as st]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(defn setup [f]
  (e/init!)
  (let [log-task (clog/init!)]
    (f)
    (la/close! log-task))
  (e/shutdown!))

(use-fixtures :once st/validate-schemas)
(use-fixtures :each setup)

(deftest test-logged
  (let [sb (StringBuilder.)
        patched-log (fn [logger level throwable message]
                      (.append sb message)
                      (.append sb "\n"))]
    (with-redefs [log/log* patched-log]
      (e/send-create-event "tester" {} "TestModelType" {:data 1})
      (e/send-event {:owner "tester" :http-params {} :data 2})
      (Thread/sleep 100)   ;; wait until the go loop is done
      (let [scrubbed (-> (str sb)
                      (str/replace #"#inst \"[^\"]*\"" "#inst \"\"")
                      (str/replace #":id event[^,]*" ":id event"))
          expected "event: {:type CreatedModel, :owner tester, :timestamp #inst \"\", :http-params {}, :model-type TestModelType, :id event, :model {:data 1}}\nevent: {:owner tester, :http-params {}, :data 2, :timestamp #inst \"\"}\n"]
      (is (= expected scrubbed))))))
