(ns ctia.stores.identity-store-test
  (:require  [clojure.test :refer [is join-fixtures testing use-fixtures]]
             [ctia.test-helpers.core :as helpers]
             [ctia.test-helpers.store :refer [deftest-for-each-store]]
             [ctia.store :as store]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean]))

(deftest-for-each-store test-read-identity
  (testing "Reading not-found identity returns nil"
    (is (nil? (store/read-identity @store/identity-store "foo")))))
