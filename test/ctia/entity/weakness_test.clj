(ns ctia.entity.weakness-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.weakness :as sut]
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
            [ctim.examples.weaknesses :refer [new-weakness-maximal new-weakness-minimal]]))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  whoami-helpers/fixture-server]))

(deftest test-weakness-routes
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
      {:app app
       :entity "weakness"
       :example new-weakness-maximal
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-weakness-pagination-field-selection
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [ids (POST-entity-bulk
                app
                (assoc new-weakness-maximal :title "foo")
                :weaknesses
                345
                {"Authorization" "45c1f5e3f05d0"})]

       (field-selection-tests
        app
        ["ctia/weakness/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/weakness-fields))

     (pagination-test
      app
      "ctia/weakness/search?query=*"
      {"Authorization" "45c1f5e3f05d0"}
      sut/weakness-fields))))

(deftest test-weakness-routes-access-control
  (access-control-test "weakness"
                       new-weakness-minimal
                       true
                       true
                       test-for-each-store-with-app))

(deftest test-weakness-metric-routes
  (test-metric-routes (into sut/weakness-entity
                            {:entity-minimal new-weakness-minimal
                             :enumerable-fields sut/weakness-enumerable-fields
                             :date-fields sut/weakness-histogram-fields})))
