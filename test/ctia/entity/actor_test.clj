(ns ctia.entity.actor-test
  (:require [clojure.test :refer [deftest use-fixtures]]
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
              validate-schemas
              whoami-helpers/fixture-server)

(def enabled-stores
  #{:actor
    :attack-pattern :incident :indicator :malware :tool :vulnerability :weakness})

(deftest test-actor-routes
  (test-for-each-store-with-app enabled-stores
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
                       (partial test-for-each-store-with-app enabled-stores)))

(deftest test-actor-metric-routes
  (test-metric-routes enabled-stores
                      (into sut/actor-entity
                            {:entity-minimal new-actor-minimal
                             :enumerable-fields sut/actor-enumerable-fields
                             :date-fields sut/actor-histogram-fields})))
