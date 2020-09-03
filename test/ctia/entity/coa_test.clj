(ns ctia.entity.coa-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.coa :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store store-fixtures]]]
            [ctim.examples.coas :refer [new-coa-maximal new-coa-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest test-coa-crud-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test {:entity "coa"
                        :example new-coa-maximal
                        :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-coa-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [ids (post-entity-bulk
                new-coa-maximal
                :coas
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        "ctia/coa/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sut/coa-fields)

       (field-selection-tests
        ["ctia/coa/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/coa-fields)))))

(deftest test-coa-routes-access-control
  (access-control-test "coa"
                       new-coa-minimal
                       true
                       true
                       test-for-each-store))

(deftest test-coa-metric-routes
  ((:es-store store-fixtures)
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "Administrators" "user")
     (test-metric-routes (into sut/coa-entity
                               {:entity-minimal new-coa-minimal
                                :enumerable-fields sut/coa-enumerable-fields
                                :date-fields sut/coa-histogram-fields})))))
