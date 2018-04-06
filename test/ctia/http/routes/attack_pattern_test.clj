(ns ctia.http.routes.attack-pattern-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [join-fixtures use-fixtures]]
            [ctia.schemas.sorting :refer [attack-pattern-sort-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [deftest-for-each-store]]]
            [ctim.examples.attack-patterns
             :refer
             [new-attack-pattern-maximal new-attack-pattern-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-attack-pattern-routes
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
                     :invalid-test-field :name}))

(deftest-for-each-store test-attack-pattern-pagination-field-selection
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
     attack-pattern-sort-fields)

    (field-selection-tests
     ["ctia/attack-pattern/search?query=*"
      (doc-id->rel-url (first ids))]
     {"Authorization" "45c1f5e3f05d0"}
     attack-pattern-sort-fields)))

(deftest-for-each-store test-attack-pattern-routes-access-control
  (access-control-test "attack-pattern"
                       new-attack-pattern-minimal
                       true
                       true))
