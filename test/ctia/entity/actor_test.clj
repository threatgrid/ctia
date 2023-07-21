(ns ctia.entity.actor-test
  (:require [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.actor :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.actors :refer [new-actor-maximal new-actor-minimal]]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :once
  (join-fixtures [validate-schemas
                  whoami-helpers/fixture-server]))

(deftest test-actor-routes
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
      (into sut/actor-entity
            {:app app
             :example new-actor-maximal
             :headers {:Authorization "45c1f5e3f05d0"}})))))

(deftest test-actor-routes-access-control
  (access-control-test "actor"
                       new-actor-minimal
                       true
                       true
                       test-for-each-store-with-app))

(deftest test-actor-metric-routes
  (test-metric-routes (into sut/actor-entity
                            {:entity-minimal new-actor-minimal
                             :enumerable-fields sut/actor-enumerable-fields
                             :date-fields sut/actor-histogram-fields})))
