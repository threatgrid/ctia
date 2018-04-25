(ns ctia.http.routes.version-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers
             [core :as helpers :refer [get]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest test-version-routes
  (test-for-each-store
   (fn []
     (testing "we can request different content types"
       (let [response (get "ctia/version" :accept :json)]
         (is (= "/ctia" (get-in response [:parsed-body "base"]))))

       (let [response (get "ctia/version" :accept :edn)]
         (is (= "/ctia" (get-in response [:parsed-body :base]) ))))

     (testing "GET /ctia/version"
       (let [response (get "ctia/version")]
         (is (= 200 (:status response)))
         (is (= schema-version (get-in response [:parsed-body :ctim-version]))))))))

(deftest test-version-headers
  (test-for-each-store
   (fn []
     (testing "GET /ctia/version"
       (let [{headers :headers
              :as response} (get "ctia/version")]
         (is (= 200 (:status response)))
         (is (every? (set (keys headers))
                     ["X-Ctia-Version"
                      "X-Ctia-Config"
                      "X-Ctim-Version"])))))))
