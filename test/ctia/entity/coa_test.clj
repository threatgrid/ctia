(ns ctia.entity.coa-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.coa :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [POST-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.coas :refer [new-coa-maximal new-coa-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
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
