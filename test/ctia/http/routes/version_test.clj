(ns ctia.http.routes.version-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers
             [core :as helpers :refer [GET]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(deftest test-version-routes
  (test-for-each-store-with-app
   (fn [app]
     (testing "we can request different content types"
       (let [response (GET app
                           "ctia/version" :accept :json)]
         (is (= "/ctia" (get-in response [:parsed-body "base"]))))

       (let [response (GET app
                           "ctia/version" :accept :edn)]
         (is (= "/ctia" (get-in response [:parsed-body :base]) ))))

     (testing "GET /ctia/version"
       (let [response (GET app
                           "ctia/version")]
         (is (= 200 (:status response)))
         (is (= schema-version (get-in response [:parsed-body :ctim-version])))
         (is (= "test" (get-in response [:parsed-body :ctia-config]))))))))

(deftest test-version-headers
  (test-for-each-store-with-app
   (fn [app]
     (testing "GET /ctia/version"
       (let [{headers :headers
              :as response} (GET app
                                 "ctia/version")]
         (is (= 200 (:status response)))
         (is (every? (set (keys headers))
                     ["X-Ctia-Version"
                      "X-Ctia-Config"
                      "X-Ctim-Version"]))
         (is (= "test" (get headers "X-Ctia-Config"))))))))
