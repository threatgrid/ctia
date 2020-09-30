(ns ctia.entity.investigation-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.investigation :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [POST-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store-with-app]]]
            [ctia.entity.investigation.examples :refer
             [new-investigation-maximal
              new-investigation-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest test-investigation-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (entity-crud-test
      {:app app
       :entity "investigation"
       :example new-investigation-maximal
       :update-tests? false
       :invalid-tests? false
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-investigation-pagination-field-selection
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [ids (POST-entity-bulk
                app
                (assoc new-investigation-maximal :title "foo")
                :investigations
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        app
        "ctia/investigation/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sut/investigation-fields)

       (field-selection-tests
        app
        ["ctia/investigation/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/investigation-fields)))))

(deftest test-investigation-routes-access-control
  (access-control-test "investigation"
                       new-investigation-minimal
                       false
                       true
                       test-for-each-store-with-app))

(deftest test-investigation-metric-routes
  (test-metric-routes (into sut/investigation-entity
                            {:entity-minimal new-investigation-minimal
                             :enumerable-fields sut/investigation-enumerable-fields
                             :date-fields sut/investigation-histogram-fields})))
