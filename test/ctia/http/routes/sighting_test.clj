(ns ctia.http.routes.sighting-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.schemas.sorting :refer [sighting-sort-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [api-key doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store]]]
            [ctim.examples.sightings
             :refer
             [new-sighting-maximal new-sighting-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(def new-sighting
  (-> new-sighting-maximal
      (dissoc :id)
      (assoc
       :tlp "green"
       :external_ids
       ["http://ex.tld/ctia/sighting/sighting-123"
        "http://ex.tld/ctia/sighting/sighting-345"])))

(deftest test-sighting-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response api-key
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      {:entity "sighting"
       :example new-sighting-maximal
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-sighting-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [ids (post-entity-bulk
                new-sighting-maximal
                :sightings
                30
                {"Authorization" "45c1f5e3f05d0"})]

       (pagination-test
        "ctia/sighting/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sighting-sort-fields)

       (field-selection-tests
        ["ctia/sighting/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sighting-sort-fields)))))

(deftest test-sighting-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "sighting"
                          new-sighting-minimal
                          true
                          true))))
