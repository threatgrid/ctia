(ns ctia.http.routes.swagger-json-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :each
  validate-schemas
  es-helpers/fixture-properties:es-store
  helpers/fixture-ctia)

(deftest test-swagger-json
  (let [app (helpers/get-current-app)]
    (testing "We can get a swagger.json"
      (is (= 200
             (:status (helpers/GET app
                                   "swagger.json"
                                   :accept :json)))))))
