(ns ctia.schemas.utils-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.gfredericks.test.chuck.generators :as chuck.gen]
            [ctia.schemas
             [core :refer [CTIMNewBundle]]
             [test-generators :as schema-gen]
             [utils :as sut]]
            [schema.core :as s]
            [schema-tools.core :as st]
            [schema-tools.walk :refer [walk]])
  (:import [clojure.lang ExceptionInfo]
           [java.util.regex Pattern]))

(defn collect-schema-version-leaves
  [collector m]
  (walk
   (fn [x]
     (if (and
          (instance? clojure.lang.IMapEntry x)
          (= :schema_version (:k (key x))))
       (do (swap! collector conj (.getName ^Class (val x))) x)
       (collect-schema-version-leaves collector x)))
   identity
   m))

(deftest recursive-open-schema-version-test
  (testing "should replace all inners for schema_version with s/Str"
    (let [res-schema (sut/recursive-open-schema-version CTIMNewBundle)
          found-leaves (atom [])]
      (collect-schema-version-leaves found-leaves res-schema)
      (is (seq @found-leaves))
      (is (every? #(= "java.lang.String" %) @found-leaves)))))

(deftest service-subgraph-test
  (let [int->v (vec (range 5))]
    (is (= (sut/service-subgraph {:a {:b (int->v 1)}}
                                 {})
           {}))
    (is (= (sut/service-subgraph
             {:a {:b (int->v 1) :c (int->v 2)}
              :d {:e (int->v 3) :f (int->v 4)}}
             {:a #{:b}})
           {:a {:b (int->v 1)}}))
    (is (= (sut/service-subgraph
             {:a {:b (int->v 1) :c (int->v 2)}
              :d {:e (int->v 3) :f (int->v 4)}}
             {:a #{:b}
              :d #{:e}})
           {:a {:b (int->v 1)}
            :d {:e (int->v 3)}}))))

(deftest service-subschema-test
  (let [int->v (let [;; distinct with stable ordering
                     ps [int? boolean? map? vector? set?]]
                 (assert (apply distinct? ps))
                 (mapv s/pred ps))]
    (is (= (sut/service-subschema {:a {:b (int->v 1)}}
                                 {})
           {}))
    (is (= (sut/service-subschema
             {:a {:b (int->v 1) :c (int->v 2)}
              :d {:e (int->v 3) :f (int->v 4)}}
             {:a #{:b}})
           {:a {:b (int->v 1)}}))
    (is (= (sut/service-subschema
             {:a {:b (int->v 1) :c (int->v 2)}
              :d {:e (int->v 3) :f (int->v 4)}}
             {:a #{:b}
              :d #{:e}})
           {:a {:b (int->v 1)}
            :d {:e (int->v 3)}}))))

(deftest select-all-keys-test
  (testing "behaves like st/select-keys with present keys"
    (is (= {}
           (sut/select-all-keys {:a s/Any} [])
           (st/select-keys {:a s/Any} [])))
    (is (= {:a s/Any}
           (sut/select-all-keys {:a s/Any} [:a])
           (st/select-keys {:a s/Any} [:a]))))
  (testing "throws with missing keys"
    (is (thrown-with-msg?
          ExceptionInfo
          (Pattern/compile "Missing keys: [:a]" Pattern/LITERAL)
          (sut/select-all-keys {} [:a])))
    (is (thrown-with-msg?
          ExceptionInfo
          (Pattern/compile "Missing keys: [:a]" Pattern/LITERAL)
          (sut/select-all-keys {:b s/Any} [:a])))
    (is (thrown-with-msg?
          ExceptionInfo
          (Pattern/compile "Missing keys: [:c]" Pattern/LITERAL)
          (sut/select-all-keys {:a s/Any :b s/Any} [:a :b :c])))))

(deftest ^:generative generative-select-all-keys-test
  (checking "coincides with st/select-keys when all keys present" 100
    [explicit-keys (gen/vector-distinct
                     gen/keyword-ns
                     {:max-elements 5})
     schema (schema-gen/map-schema
              {:explicit-keys explicit-keys})
     selection (chuck.gen/subsequence explicit-keys)]
    (is (= (sut/select-all-keys schema selection)
           (st/select-keys schema selection))))
  (checking "missing keys trigger errors" 100
    [explicit-keys (gen/vector-distinct
                     gen/keyword-ns
                     {:max-elements 5})
     missing-keys (gen/set
                    (gen/such-that
                      (complement (set explicit-keys))
                      gen/keyword-ns
                      100)
                    {:min-elements 1
                     :max-elements 10})
     schema (schema-gen/map-schema
              {:explicit-keys explicit-keys})
     present-selection (chuck.gen/subsequence explicit-keys)
     missing-selection (gen/not-empty
                         (chuck.gen/subsequence missing-keys))
     :let [selection (into (set present-selection)
                           missing-selection)
           expected-missing (-> (set/difference selection
                                                present-selection)
                                sort
                                vec
                                (doto (-> seq assert)))]]
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          (Pattern/compile
            (str "Missing keys: " expected-missing)
            Pattern/LITERAL)
          (sut/select-all-keys
            schema
            selection)))))
