(ns ctia.http.routes.incident-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.incident :refer [incident-fields]]
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
            [ctim.examples.incidents
             :refer
             [new-incident-maximal new-incident-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest test-incident-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "foogroup" "user")
     (entity-crud-test
      {:entity "incident"
       :example new-incident-maximal
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-incident-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [ids (post-entity-bulk
                (assoc new-incident-maximal :title "foo")
                :incidents
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        "ctia/incident/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        incident-fields)

       (field-selection-tests
        ["ctia/incident/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        incident-fields)))))

(deftest test-incident-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "incident"
                          new-incident-minimal
                          true
                          true))))
