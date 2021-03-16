(ns ctia.entity.attack-pattern-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.attack-pattern :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [POST-entity-bulk ]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.attack-patterns
             :refer
             [new-attack-pattern-maximal new-attack-pattern-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(deftest test-attack-pattern-crud-routes
  (test-for-each-store-with-app
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
      (into sut/attack-pattern-entity
            {:app app
             :example new-attack-pattern-maximal
             :headers {:Authorization "45c1f5e3f05d0"}
             :update-field :title
             :invalid-test-field :title})))))

(deftest attack-pattern-routes-access-control
  (access-control-test "attack-pattern"
                       new-attack-pattern-minimal
                       true
                       true
                       test-for-each-store-with-app))

(deftest test-attack-pattern-metric-routes
  (test-metric-routes (into sut/attack-pattern-entity
                            {:plural :attack_patterns
                             :entity-minimal new-attack-pattern-minimal
                             :enumerable-fields sut/attack-pattern-enumerable-fields
                             :date-fields sut/attack-pattern-histogram-fields})))
