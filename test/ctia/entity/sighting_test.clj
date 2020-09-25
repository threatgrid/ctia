(ns ctia.entity.sighting-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.sighting :as sut]
            [ctia.entity.sighting.schemas :refer [sighting-sort-fields
                                                  sighting-fields
                                                  sighting-enumerable-fields
                                                  sighting-histogram-fields
                                                  NewSighting]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-entity-bulk
                                       post-bulk]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [api-key doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store-with-app]]]
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

(deftest test-sighting-crud-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response api-key
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      {:entity "sighting"
       :example new-sighting-maximal
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-sighting-metric-routes
  (test-metric-routes (into sut/sighting-entity
                            {:entity-minimal new-sighting-minimal
                             :enumerable-fields sighting-enumerable-fields
                             :date-fields sighting-histogram-fields})))

(deftest test-sighting-pagination-field-selection
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [ids (post-entity-bulk
                new-sighting-maximal
                :sightings
                30
                {"Authorization" "45c1f5e3f05d0"})

           sample (dissoc new-sighting-maximal :id)
           first-sighting (-> sample
                              (assoc-in [:observed_time :start_time]
                                        #inst "2016-01-01T01:01:01.000Z"))
           second-sighting (-> sample
                               (assoc-in [:observed_time :start_time]
                                         #inst "2016-01-02T01:01:01.000Z"))
           third-sighting (-> sample
                              (assoc :timestamp
                                     #inst "2016-01-03T01:01:01.000Z")
                              (assoc-in [:observed_time :start_time]
                                        #inst "2016-01-02T01:01:01.000Z"))
           custom-samples (post-bulk {:sightings [first-sighting
                                                  second-sighting
                                                  third-sighting]})]
       (pagination-test
        "ctia/sighting/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sighting-sort-fields)

       (field-selection-tests
        ["ctia/sighting/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sighting-fields)))))

(deftest test-sighting-routes-access-control
  (access-control-test "sighting"
                       new-sighting-minimal
                       true
                       true
                       test-for-each-store-with-app))
