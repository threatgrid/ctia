(ns ctia.entity.investigation-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.investigation :refer [investigation-fields]]
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
            [ctim.examples.investigations
             :refer
             [new-investigation-maximal new-investigation-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest test-investigation-routes
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
     (entity-crud-test
      {:entity "investigation"
       :example new-investigation-maximal
       :update-tests? false
       :invalid-tests? false
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-investigation-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [ids (post-entity-bulk
                (assoc new-investigation-maximal :title "foo")
                :investigations
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        "ctia/investigation/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        investigation-fields)

       (field-selection-tests
        ["ctia/investigation/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        investigation-fields)))))

(deftest test-investigation-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "investigation"
                          new-investigation-minimal
                          false
                          true))))
