(ns ctia.flows.crud-test
  (:require [ctia.flows.crud :as sut]
            [ctia.lib.collection :as coll]
            [clj-momo.test-helpers
             [core :as mth]]
            [clj-momo.lib.map :refer [deep-merge-with]]
            [clojure.test :refer [deftest testing is]]))

(deftest deep-merge-with-add-colls-test
  (let [fixture {:foo {:bar ["one" "two" "three"]
                       :lorem ["ipsum" "dolor"]}}]
    (is (deep= {:foo {:bar ["one" "two" "three" "four"]
                      :lorem ["ipsum" "dolor"]}}
               (deep-merge-with coll/add-colls
                                fixture
                                {:foo {:bar ["four"]}})))))

(deftest deep-merge-with-remove-colls-test
  (let [fixture {:foo {:bar #{"one" "two" "three"}
                       :lorem ["ipsum" "dolor"]}}]
    (is (deep= {:foo {:bar #{"one" "three"}
                      :lorem ["ipsum" "dolor"]}}
               (deep-merge-with coll/remove-colls
                                fixture
                                {:foo {:bar ["two"]}})))))

(deftest deep-merge-with-replace-colls-test
  (let [fixture {:foo {:bar {:foo {:bar ["something" "or" "something" "else"]}}
                       :lorem ["ipsum" "dolor"]}}]
    (is (deep= {:foo {:bar {:foo {:bar ["else"]}}
                      :lorem ["ipsum" "dolor"]}}
               (deep-merge-with coll/replace-colls
                                fixture
                                {:foo  {:bar {:foo {:bar #{"else"}}}}})))))
