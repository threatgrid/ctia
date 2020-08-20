(ns ctia.bulk.core-test
  (:require [ctia.bulk.core :as sut :refer [read-fn]]
            [ctia.test-helpers.core :as helpers]
            [clojure.test :refer [deftest testing is]]))

(deftest read-entities-test
  (testing "Attempting to read an unreachable entity should not throw"
    (let [get-in-config (helpers/build-get-in-config-fn)
          res (sut/read-entities ["judgement-123"]
                                 :judgement {}
                                 {:ConfigService {:get-in-config get-in-config}
                                  :StoreService {:read-store (constantly nil)}})]
      (is (= [nil] res)))))
