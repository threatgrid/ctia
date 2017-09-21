(ns ctia.stores.identity-store-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.store :as store]
            [ctia.test-helpers
             [core :as helpers]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean]))

(deftest-for-each-store test-read-identity
  (testing "Reading not-found identity returns nil"

    (store/write-store :identity
                       (fn [store]
                         (store/create-identity store {:login "bar"
                                                       :groups ["foogroup"]
                                                       :capabilities #{:read-actor}
                                                       :role :admin})))

    (is (nil? (store/read-store :identity
                                (fn [store]
                                  (store/read-identity store "foo")))))))
