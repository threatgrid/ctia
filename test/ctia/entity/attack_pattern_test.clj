(ns ctia.entity.attack-pattern-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.attack-pattern :refer [attack-pattern-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-entity-bulk ]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store]]]
            [ctim.examples.attack-patterns
             :refer
             [new-attack-pattern-maximal new-attack-pattern-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest test-attack-pattern-routes
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
        attack-pattern-fields)

       (field-selection-tests
        ["ctia/attack-pattern/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        attack-pattern-fields)))))

(deftest attack-pattern-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "attack-pattern"
                          new-attack-pattern-minimal
                          true
                          true))))
