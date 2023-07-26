(ns ctia.entity.investigation-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.investigation :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctia.entity.investigation.examples :refer
             [new-investigation-maximal
              new-investigation-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(def enabled-stores #{:investigation :tool :attack-pattern :incident :casebook :malware})

(deftest test-investigation-routes
  (test-for-each-store-with-app
   enabled-stores
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (entity-crud-test
      (into sut/investigation-entity
            {:app app
             :example new-investigation-maximal
             :update-tests? false
             :invalid-tests? false
             :delete-search-tests? false
             :headers {:Authorization "45c1f5e3f05d0"}})))))

(deftest test-investigation-routes-access-control
  (access-control-test "investigation"
                       new-investigation-minimal
                       false
                       true
                       (partial test-for-each-store-with-app enabled-stores)))

(deftest test-investigation-metric-routes
  (test-metric-routes enabled-stores
                      (into sut/investigation-entity
                            {:entity-minimal new-investigation-minimal
                             :enumerable-fields sut/investigation-enumerable-fields
                             :date-fields sut/investigation-histogram-fields})))
