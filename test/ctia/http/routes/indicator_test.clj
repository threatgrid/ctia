(ns ctia.http.routes.indicator-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [join-fixtures use-fixtures]]
            [ctia.auth :as auth]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [core :as helpers]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]
            [ctim.examples.indicators
             :refer
             [new-indicator-maximal new-indicator-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-indicator-routes
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")
  (entity-crud-test
   {:entity "indicator"
    :example new-indicator-maximal
    :headers {:Authorization "45c1f5e3f05d0"}}))

(deftest-for-each-store test-indicator-routes-access-control
  (access-control-test "indicator"
                       new-indicator-minimal
                       true
                       false))
