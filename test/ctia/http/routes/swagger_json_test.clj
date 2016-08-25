(ns ctia.http.routes.swagger-json-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.test-helpers.core :as helpers]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    helpers/fixture-ctia]))

(deftest test-swagger-json
  (testing "We can get a swagger.json"
    (is (= 200
           (:status (helpers/get "swagger.json"
                                 :accept :json))))))
