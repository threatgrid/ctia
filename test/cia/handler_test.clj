(ns cia.handler-test
  (:require [cia.handler :as handler]
            [cia.test-helpers :refer [fixture-server get]]
            [clj-http.client :as http]
            [clojure.test :refer  [deftest is testing use-fixtures]]))

(use-fixtures :once (fixture-server handler/app))

(deftest test-handler-routes
  (testing "we can request different content types"
    (let [response (get "cia/version" :accept :json)]
      (is (= "/cia" (get-in response [:parsed-body "base"]))))

    (let [response (get "cia/version" :accept :edn)]
      (is (= "/cia" (get-in response [:parsed-body :base]) ))))

  (testing "cia/version"
    (let [response (get "cia/version")]
      (is (= 200 (:status response)))
      (is (= "0.1" (get-in response [:parsed-body :version]))))))
