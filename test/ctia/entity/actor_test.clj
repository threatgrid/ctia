(ns ctia.entity.actor-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.actor :as sut]
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
            [ctim.examples.actors :refer [new-actor-maximal new-actor-minimal]]))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest test-actor-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app
                                "foouser"
                                ["foogroup"]
                                "user"
                                all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      {:app app
       :entity "actor"
       :example new-actor-maximal
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-actor-pagination-field-selection
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [ids (POST-entity-bulk
                app
                (assoc new-actor-maximal :title "foo")
                :actors
                345
                {"Authorization" "45c1f5e3f05d0"})]

       (field-selection-tests
        app
        ["ctia/actor/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/actor-fields))

     (pagination-test
      app
      "ctia/actor/search?query=*"
      {"Authorization" "45c1f5e3f05d0"}
      sut/actor-fields))))

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
