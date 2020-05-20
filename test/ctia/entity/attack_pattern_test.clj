(ns ctia.entity.attack-pattern-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.attack-pattern :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-entity-bulk ]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store store-fixtures]]]
            [ctim.examples.attack-patterns
             :refer
             [new-attack-pattern-maximal new-attack-pattern-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest test-attack-pattern-crud-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser"
                                ["foogroup"]
                                "user"
                                all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (entity-crud-test {:entity "attack-pattern"
                        :example new-attack-pattern-maximal
                        :headers {:Authorization "45c1f5e3f05d0"}
                        :update-field :name
                        :invalid-test-field :name}))))

(deftest test-attack-pattern-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [ids (post-entity-bulk
                new-attack-pattern-maximal
                :attack_patterns
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        "ctia/attack-pattern/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sut/attack-pattern-fields)

       (field-selection-tests
        ["ctia/attack-pattern/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/attack-pattern-fields)))))

(deftest attack-pattern-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "attack-pattern"
                          new-attack-pattern-minimal
                          true
                          true))))

(deftest test-attack-pattern-metric-routes
  ((:es-store store-fixtures)
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "Administrators" "user")
     (test-metric-routes (into sut/attack-pattern-entity
                               {:plural :attack_patterns
                                :entity-minimal new-attack-pattern-minimal
                                :enumerable-fields sut/attack-pattern-enumerable-fields
                                :date-fields sut/attack-pattern-histogram-fields})))))
