(ns ctia.http.routes.incident-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [join-fixtures use-fixtures]]
            [ctia.schemas.sorting :refer [incident-sort-fields]]
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
            [ctim.examples.incidents :refer [new-incident-minimal
                                             new-incident-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-incident-routes
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "foogroup" "user")

  (entity-crud-test
   {:entity "incident"
    :example new-incident-maximal
    :headers {:Authorization "45c1f5e3f05d0"}}))

(deftest-for-each-store test-incident-pagination-field-selection
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
     incident-sort-fields)

    (field-selection-tests
     ["ctia/incident/search?query=*"
      (doc-id->rel-url (first ids))]
     {"Authorization" "45c1f5e3f05d0"}
     incident-sort-fields)))

(deftest-for-each-store test-incident-routes-access-control
  (access-control-test "incident"
                       new-incident-minimal
                       true
                       true))
