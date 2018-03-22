(ns ctia.http.routes.version-test
  (:refer-clojure :exclude [get])
  (:require
    [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
    [clj-momo.test-helpers.core :as mth]
    [ctia.domain.entities :refer [schema-version]]
    [ctim.schemas.common :as c]
    [ctia.test-helpers
     [auth :refer [all-capabilities]]
     [core :as helpers :refer [delete get post put]]
     [fake-whoami-service :as whoami-helpers]
     [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-version-routes
  (testing "we can request different content types"
    (let [response (get "ctia/version" :accept :json)]
      (is (= "/ctia" (get-in response [:parsed-body "base"]))))

    (let [response (get "ctia/version" :accept :edn)]
      (is (= "/ctia" (get-in response [:parsed-body :base]) ))))

  (testing "GET /ctia/version"
    (let [response (get "ctia/version")]
      (is (= 200 (:status response)))
      (is (= schema-version (get-in response [:parsed-body :ctim-version]))))))


(deftest-for-each-store test-version-headers
  (testing "GET /ctia/version"
    (let [{headers :headers
           :as response} (get "ctia/version")]
      (is (= 200 (:status response)))
      (is (every? (set (keys headers))
                  ["X-Ctia-Version"
                   "X-Ctia-Config"
                   "X-Ctim-Version"])))))
