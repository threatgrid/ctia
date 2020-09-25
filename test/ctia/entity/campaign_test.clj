(ns ctia.entity.campaign-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.campaign :as sut]
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
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.campaigns :as ex :refer [new-campaign-maximal new-campaign-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest test-campaign-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (entity-crud-test {:entity "campaign"
                        :example (assoc new-campaign-maximal :tlp "green")
                        :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-campaign-pagination-field-selection
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [ids (post-entity-bulk
                (assoc new-campaign-maximal :title "foo")
                :campaigns
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        "ctia/campaign/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sut/campaign-fields)

       (field-selection-tests
        ["ctia/campaign/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/campaign-fields)))))

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
