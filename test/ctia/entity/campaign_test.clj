(ns ctia.entity.campaign-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.campaign :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.campaigns :as ex :refer [new-campaign-maximal new-campaign-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(deftest test-campaign-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (entity-crud-test
      (into sut/campaign-entity
            {:app app
             :example (assoc new-campaign-maximal :tlp "green")
             :headers {:Authorization "45c1f5e3f05d0"}})))))

(deftest test-campaign-routes-access-control
  (access-control-test "campaign"
                       ex/new-campaign-minimal
                       true
                       true
                       test-for-each-store-with-app))

(deftest test-campaign-metric-routes
  (test-metric-routes (into sut/campaign-entity
                            {:entity-minimal new-campaign-minimal
                             :enumerable-fields sut/campaign-enumerable-fields
                             :date-fields sut/campaign-histogram-fields})))
