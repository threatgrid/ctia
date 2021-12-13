(ns ctia.entity.sighting-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.sighting :as sut]
            [ctia.entity.sighting.schemas
             :refer
             [sighting-enumerable-fields sighting-histogram-fields]]
            [ctia.test-helpers.access-control :refer [access-control-test]]
            [ctia.test-helpers.aggregate :refer [test-metric-routes]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.http :refer [api-key]]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.examples.sightings
             :refer
             [new-sighting-maximal new-sighting-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

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
     (whoami-helpers/set-whoami-response app
                                         api-key
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      (into sut/sighting-entity
            {:app app
             :example new-sighting-maximal
             ;; set search-tests? to false to quickly test crud
             :search-tests? true
             :headers {:Authorization "45c1f5e3f05d0"}})))))

(deftest test-sighting-metric-routes
  (test-metric-routes (into sut/sighting-entity
                            {:entity-minimal new-sighting-minimal
                             :enumerable-fields sighting-enumerable-fields
                             :date-fields sighting-histogram-fields})))

(deftest test-sighting-routes-access-control
  (access-control-test "sighting"
                       new-sighting-minimal
                       true
                       true
                       test-for-each-store-with-app))
