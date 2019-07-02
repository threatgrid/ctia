(ns ctia.bulk.core-test
  (:require [ctia.bulk.core :as sut :refer [read-fn]]
            [clojure.test :refer [deftest testing is]]))

(deftest read-entities-test
  (testing "Attempting to read an unreachable entity should not throw"
    (let [res (with-redefs [ctia.bulk.core/read-fn (fn [_ _ _] (fn [id] nil))]
                (sut/read-entities ["judgement-123"]
                                   :judgement {}))]
      (is (= [nil] res)))))
