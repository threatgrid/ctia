(ns ctia.lib.collection-test
  (:require [ctia.lib.collection :as sut]
            [clojure.test :refer [deftest is]]))

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
