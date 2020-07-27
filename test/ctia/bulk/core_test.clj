(ns ctia.bulk.core-test
  (:require [ctia.bulk.core :as sut :refer [read-fn]]
            [clojure.test :refer [deftest testing is]]))

(deftest read-entities-test
  (testing "Attempting to read an unreachable entity should not throw"
    (let [res (sut/read-entities ["judgement-123"]
                                 :judgement {}
                                 {:StoreService {:read-store (constantly nil)}})]
      (is (= [nil] res)))))
