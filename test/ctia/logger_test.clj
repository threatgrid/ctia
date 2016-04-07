(ns ctia.logger-test
  (:require [ctia.events :as c :refer :all]
            [clojure.test :as t :refer :all]
            [schema.test :as st]))

(use-fixtures :once st/validate-schemas)

(deftest test-setup
  (init!)
  (let [{b :chan-buf c :chan m :mult} @central-channel]
    (send-create-event "tester" {} "TestModelType" {:data 1})
    (send-create-event "tester" {} "TestModelType" {:data 2})
    (send-event {:owner "tester" :http-params {} :data 3})))
  
