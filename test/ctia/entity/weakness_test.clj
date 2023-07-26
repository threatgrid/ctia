(ns ctia.entity.weakness-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.weakness :as sut]
            [ctia.test-helpers.access-control :refer [access-control-test]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.aggregate :refer [test-metric-routes]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.examples.weaknesses :refer [new-weakness-maximal new-weakness-minimal]]))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  whoami-helpers/fixture-server]))

(def enabled-stores #{:tool :weakness :attack-pattern :incident :casebook :malware})

(deftest test-weakness-routes
  (test-for-each-store-with-app
   enabled-stores
   (fn [app]
     (helpers/set-capabilities! app
                                "foouser"
                                ["foogroup"]
                                "user"
                                all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      (into sut/weakness-entity
            {:app app
             :example new-weakness-maximal
             :headers {:Authorization "45c1f5e3f05d0"}})))))

(deftest test-weakness-routes-access-control
  (access-control-test "weakness"
                       new-weakness-minimal
                       true
                       true
                       (partial test-for-each-store-with-app enabled-stores)))

(deftest test-weakness-metric-routes
  (test-metric-routes enabled-stores
                      (into sut/weakness-entity
                            {:entity-minimal new-weakness-minimal
                             :enumerable-fields sut/weakness-enumerable-fields
                             :date-fields sut/weakness-histogram-fields})))
