(ns ctia.schemas.utils-test
  (:require [clojure.test :refer [deftest is testing]]
            [ctia.schemas
             [core :refer [CTIMNewBundle]]
             [utils :as sut]]
            [schema-tools.walk :refer [walk]]))

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

(defn service-subgraph-test* [service-subgraph int->v]
  {:pre [(vector? int->v)
         (>= 5 (count int->v))
         (apply distinct? int->v)]}
  (is (= (service-subgraph {:a {:b (int->v 1)}})
         {}))
  (is (= (service-subgraph
           {:a {:b (int->v 1) :c (int->v 2)}
            :d {:e (int->v 3) :f (int->v 4)}}
           :a [:b])
         {:a {:b (int->v 1)}}))
  (is (= (service-subgraph
           {:a {:b (int->v 1) :c (int->v 2)}
            :d {:e (int->v 3) :f (int->v 4)}}
           :a [:b]
           :d [:e])
         {:a {:b (int->v 1)}
          :d {:e (int->v 3)}}))
  (testing "throws on uneven args"
    (is (thrown-with-msg?
          AssertionError
          #"Uneven number of selectors"
          (service-subgraph
            {}
            :b)))
    (is (thrown-with-msg?
          AssertionError
          #"Uneven number of selectors"
          (service-subgraph
            {}
            :b [:c]
            :d))))
  (testing "throws when selections clobber"
    (is (thrown?
          AssertionError
          #"Repeated key :a"
          (service-subgraph
            {:a {:b (int->v 1)}}
            :a [:b]
            :a [:b])))))

(deftest service-subgraph-test
  (service-subgraph-test*
    sut/service-subgraph
    (vec (range 5))))

(deftest service-subschema-test
  (service-subgraph-test*
    sut/service-subgraph
    (let [;; distinct with stable ordering
          ps [int? boolean? map? vector? set?]]
      (assert (apply distinct? ps))
      (mapv s/pred ps))))
