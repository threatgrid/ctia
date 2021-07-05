(ns ctia.entity.coa-test
  (:require [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.coa :as sut]
            [ctia.test-helpers.access-control :refer [access-control-test]]
            [ctia.test-helpers.aggregate :refer [test-metric-routes]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.examples.coas :refer [new-coa-maximal new-coa-minimal]]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :once (join-fixtures [validate-schemas
                                    whoami-helpers/fixture-server]))

(deftest test-coa-crud-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      (into sut/coa-entity
            {:app app
             :example new-coa-maximal
             :headers {:Authorization "45c1f5e3f05d0"}})))))

(deftest test-coa-routes-access-control
  (access-control-test "coa"
                       new-coa-minimal
                       true
                       true
                       test-for-each-store-with-app))

(deftest test-coa-metric-routes
  (test-metric-routes (into sut/coa-entity
                            {:entity-minimal new-coa-minimal
                             :enumerable-fields sut/coa-enumerable-fields
                             :date-fields sut/coa-histogram-fields})))
