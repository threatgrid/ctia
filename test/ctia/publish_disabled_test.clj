(ns ctia.publish-disabled-test
  (:require [clojure.test :refer :all]
            [ctia.subscribe :as sub]
            [ctia.events :as e]
            [ctia.lib.async :as la]
            [ctia.properties :refer [properties]]
            [ctia.test-helpers.core :as test-helpers]
            [clojure.core.async :as a]))

(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    test-helpers/fixture-ctia-fast]))

(deftest ^:integration test-events-with-redis-disabled
  (testing "error thrown when subscribing to disabled redis"

    (is (false? (get-in @properties [:ctia :store :redis :enabled])))
    (is (thrown? AssertionError
                 (sub/event-subscribe (constantly true))))))
