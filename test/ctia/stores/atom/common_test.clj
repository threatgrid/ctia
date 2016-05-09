(ns ctia.stores.atom.common-test
  (:require [ctia.stores.atom.common :as sut]
            [schema.core :as s]
            [clojure.test :refer [is] :as t]))

;; Access private function `match?`
(def match? #'sut/match?)

(t/deftest match?-works-correctly
  (t/testing "Simple match"
    (t/are [x y] (= x y)
      ;; simple
      true (match? :a :a)
      false (match? :a :b)))
  (t/testing "source is a coll, and search for is not a set"
    (t/are [x y] (= x y)
      true (match? [:a :b] :a)
      true (match? [:a :b] :b)
      false (match? [:a :b] :c)
      false (match? [] :a)
      ;; these ones are tricky but these should be false
      false (match? [:a :b] [:a :b])
      false (match? [] [])))
  (t/testing "source is a simple value, search for is a set"
    (t/are [x y] (= x y)
      ;; the searched value is a set
      true (match? :a #{:a})
      true (match? :a #{:b :c :a})))
  (t/testing "source is a coll and search for is a set"
    (t/are [x y] (= x y)
      true (match? [:a :b] #{:a})
      true (match? [:a :b] #{:b})
      false (match? [:a :b] #{:c})
      false (match? [:a :b] #{}))))

(s/defschema TestSchema
  {:a s/Keyword
   :b [s/Keyword]
   :c {:d s/Keyword
       :e [s/Keyword]}})


(def id1 {:a :foo
          :b [:bar :baz]
          :c {:d :foo
              :e [:bar :baz]}})
(def id2 (dissoc id1 :a))
(def id3 (dissoc id1 :b))
(def id4 (update-in id1 [:c] dissoc :d))
(def id5 (update-in id1 [:c] dissoc :e))
(def st (atom {:id1 id1
               :id2 id2
               :id3 id3
               :id4 id4
               :id5 id5}))
(def lst-fn (sut/list-handler TestSchema))


(t/deftest empty-search
  (is (= nil
         (lst-fn st {}))))
(t/deftest simple-values
  (is (= #{id1 id3 id4 id5}
         (set (lst-fn st {:a :foo})))))
(t/deftest simple-values-no-result
  (is (= #{}
         (set (lst-fn st {:a :bar})))))
(t/deftest simple-value-via-inner-key
  (is (= #{id1 id2 id3 id5}
         (set (lst-fn st {[:c :d] :foo})))))
(t/deftest simple-value-via-inner-key-no-result
  (is (= #{}
         (set (lst-fn st {[:c :d] :bar})))))
(t/deftest search-set-of-values-in-simple-key
  (is (= #{id1 id3 id4 id5}
         (set (lst-fn st {:a #{:foo :quux}})))))
(t/deftest search-set-of-values-in-simple-key-no-result
  (is (= #{}
         (set (lst-fn st {:a #{:quux}})))))
(t/deftest search-set-of-values-via-inner-key
  (is (= #{id1 id2 id3 id5}
         (set (lst-fn st {[:c :d] #{:foo :quux}})))))
(t/deftest search-set-of-values-via-inner-key-no-result
  (is (= #{}
         (set (lst-fn st {[:c :d] #{:quux}})))))
;; Model values contains collections
(t/deftest model-coll-simple-values
  (is (= #{id1 id2 id4 id5}
         (set (lst-fn st {:b :bar})))))
(t/deftest model-coll-simple-values-no-result
  (is (= #{}
         (set (lst-fn st {:b :foo})))))
(t/deftest model-coll-simple-value-via-inner-key
  (is (= #{id1 id2 id3 id4}
         (set (lst-fn st {[:c :e] :bar})))))
(t/deftest model-coll-simple-value-via-inner-key-no-result
  (is (= #{}
         (set (lst-fn st {[:c :e] :foo})))))
(t/deftest model-coll-search-set-of-values-in-simple-key
  (is (= #{id1 id2 id4 id5}
         (set (lst-fn st {:b #{:bar :quux}})))))
(t/deftest model-coll-search-set-of-values-in-simple-key-no-result
  (is (= #{}
         (set (lst-fn st {:b #{:quux}})))))
(t/deftest model-coll-search-set-of-values-via-inner-key
  (is (= #{id1 id2 id3 id4}
         (set (lst-fn st {[:c :e] #{:bar :quux}})))))
(t/deftest model-coll-search-set-of-values-via-inner-key-no-result
  (is (= #{}
         (set (lst-fn st {[:c :e] #{:quux}})))))
