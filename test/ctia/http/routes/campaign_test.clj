(ns ctia.http.routes.campaign-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.schemas.sorting :refer [campaign-sort-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store]]]
            [ctim.examples.campaigns :as ex :refer [new-campaign-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest test-campaign-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (entity-crud-test {:entity "campaign"
                        :example (assoc new-campaign-maximal :tlp "green")
                        :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-campaign-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
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
        campaign-sort-fields)

       (field-selection-tests
        ["ctia/campaign/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        campaign-sort-fields)))))

(deftest test-campaign-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "campaign"
                          ex/new-campaign-minimal
                          true
                          true))))
