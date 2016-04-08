(ns ctia.logger-test
  (:require [ctia.logging :refer :all]
            [ctia.events :as c :refer :all]
            [clojure.test :as t :refer :all]
            [schema.test :as st]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(use-fixtures :once st/validate-schemas)

(deftest test-setup
  (init!)
  (let [{b :chan-buf c :chan m :mult :as ev} @central-channel]
    (log-channel ev)
    (send-create-event "tester" {} "TestModelType" {:data 1})
    (Thread/sleep 100))) ;; wait until the go loop is done
  
(deftest test-logged
  (init!)
  (let [{b :chan-buf c :chan m :mult :as ev} @central-channel
        sb (StringBuilder.)
        ll log/log*
        patched-log (fn [logger level throwable message]
                      (.append sb message)
                      (.append sb "\n"))]
    (alter-var-root (var log/log*) (constantly patched-log))
    (log-channel ev)
    (send-create-event "tester" {} "TestModelType" {:data 1})
    (send-event {:owner "tester" :http-params {} :data 2})
    (Thread/sleep 100)   ;; wait until the go loop is done
    (let [scrubbed (-> (str sb)
                       (str/replace #"#inst \"[^\"]*\"" "#inst \"\"")
                       (str/replace #":id event[^,]*" ":id event"))
          expected "event: {:type CreatedModel, :owner tester, :timestamp #inst \"\", :http-params {}, :model-type TestModelType, :id event, :model {:data 1}}\nevent: {:owner tester, :http-params {}, :data 2, :timestamp #inst \"\"}\n"]
      (is (= expected scrubbed)))
    (alter-var-root (var log/log*) (constantly ll))))
