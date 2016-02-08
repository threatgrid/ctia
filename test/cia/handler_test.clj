(ns cia.handler-test
  (:refer-clojure :exclude [get])
  (:require [cia.handler :as handler]
            [cia.test-helpers :refer [get post delete] :as helpers]
            [clj-http.client :as http]
            [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]))

(use-fixtures :each (join-fixtures [(helpers/fixture-server handler/app)
                                    helpers/fixture-clean-state
                                    helpers/fixture-schema-validation]))

(deftest test-version-routes
  (testing "we can request different content types"
    (let [response (get "cia/version" :accept :json)]
      (is (= "/cia" (get-in response [:parsed-body "base"]))))

    (let [response (get "cia/version" :accept :edn)]
      (is (= "/cia" (get-in response [:parsed-body :base]) ))))

  (testing "GET /cia/version"
    (let [response (get "cia/version")]
      (is (= 200 (:status response)))
      (is (= "0.1" (get-in response [:parsed-body :version]))))))
