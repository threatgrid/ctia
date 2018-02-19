(ns ctia.flows.crud-test
  (:require [ctia.flows.crud :as sut]
            [clj-momo.test-helpers
             [core :as mth]]
            [clj-momo.lib.map :refer [deep-merge-with]]
            [clojure.test :refer [deftest testing is]]))

(deftest add-colls-test
  (let [input [#{:a :b} [:c :d] nil [:e]]]
    (is (deep= #{:a :b :c :d :e}
               (apply sut/add-colls input)))))

(deftest remove-colls-test
  (let [input [[:a :b :c :d :e] #{:c :d} [:e] nil]]
    (is (deep= [:a :b]
               (apply sut/remove-colls input)))))

(deftest replace-colls-test
  (let [input [#{:a :b} [:c :d] nil [:e :f]]]
    (is (deep= #{:e :f}
               (apply sut/replace-colls input)))))

(deftest deep-merge-with-add-colls-test
  (let [fixture {:foo {:bar ["one" "two" "three"]
                       :lorem ["ipsum" "dolor"]}}]
    (is (deep= {:foo {:bar ["one" "two" "three" "four"]
                      :lorem ["ipsum" "dolor"]}}
               (deep-merge-with sut/add-colls
                                fixture
                                {:foo {:bar ["four"]}})))))

(deftest deep-merge-with-remove-colls-test
  (let [fixture {:foo {:bar #{"one" "two" "three"}
                       :lorem ["ipsum" "dolor"]}}]
    (is (deep= {:foo {:bar #{"one" "three"}
                      :lorem ["ipsum" "dolor"]}}
               (deep-merge-with sut/remove-colls
                                fixture
                                {:foo {:bar ["two"]}})))))

(deftest deep-merge-with-replace-colls-test
  (let [fixture {:foo {:bar {:foo {:bar ["something" "or" "something" "else"]}}
                       :lorem ["ipsum" "dolor"]}}]
    (is (deep= {:foo {:bar {:foo {:bar ["else"]}}
                      :lorem ["ipsum" "dolor"]}}
               (deep-merge-with sut/replace-colls
                                fixture
                                {:foo  {:bar {:foo {:bar #{"else"}}}}})))))
