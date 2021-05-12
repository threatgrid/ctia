(ns ctia.schemas.utils-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.set :as set]
            [clojure.test :refer [deftest is testing use-fixtures]]
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

(use-fixtures :once mth/fixture-schema-validation)

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
  (is (= (service-subgraph {:a {:b (int->v 1)}}
                           {})
         {}))
  (is (= (service-subgraph
           {:a {:b (int->v 1) :c (int->v 2)}
            :d {:e (int->v 3) :f (int->v 4)}}
           {:a #{:b}})
         {:a {:b (int->v 1)}}))
  (is (= (service-subgraph
           {:a {:b (int->v 1) :c (int->v 2)}
            :d {:e (int->v 3) :f (int->v 4)}}
           {:a #{:b}
            :d #{:e}})
         {:a {:b (int->v 1)}
          :d {:e (int->v 3)}})))

(deftest service-subgraph-test
  (let [int->v (vec (range 5))]
    (service-subgraph-test*
      sut/select-service-subgraph
      (vec (range 5)))
    (testing "optional keys"
      (is (= (sut/select-service-subgraph
               {:a {:b (int->v 1) :c (int->v 2)}
                :d {:e (int->v 3) :f (int->v 4)}}
               {:a #{:b (s/optional-key :not-here)}
                (s/optional-key :e) #{:gone}})
             {:a {:b (int->v 1)}}))))
  (testing "missing service function throws"
    (is (thrown-with-msg?
          ExceptionInfo
          (Pattern/compile
            "Missing service: :MissingService"
            Pattern/LITERAL)
          (sut/select-service-subgraph
            {}
            {:MissingService #{:foo}})))
    (is (thrown-with-msg?
          ExceptionInfo
          (Pattern/compile
            "Missing service: :MissingService"
            Pattern/LITERAL)
          (sut/select-service-subgraph
            {:PresentService {:present (constantly nil)}}
            {:MissingService #{:foo :bar}
             :PresentService #{:present}})))
    (is (thrown-with-msg?
          ExceptionInfo
          (Pattern/compile
            "Missing :PresentService service function: :missing"
            Pattern/LITERAL)
          (sut/select-service-subgraph
            {:PresentService {:present (constantly nil)}}
            {:PresentService #{:present :missing}})))))

(deftest select-service-subschema-test
  (service-subgraph-test*
    sut/select-service-subschema
    (let [;; distinct with stable ordering
          ps [int? boolean? map? vector? set?]]
      (assert (apply distinct? ps))
      (mapv s/pred ps)))
  (testing "missing service function throws"
    (is (thrown-with-msg?
          ExceptionInfo
          (Pattern/compile
            "Missing service: :MissingService"
            Pattern/LITERAL)
          (sut/select-service-subschema
            {}
            {:MissingService #{:foo}})))
    (is (thrown-with-msg?
          ExceptionInfo
          (Pattern/compile
            "Missing service: :MissingService"
            Pattern/LITERAL)
          (sut/select-service-subschema
            {:PresentService {:present (constantly nil)}}
            {:MissingService #{:foo :bar}
             :PresentService #{:present}})))
    (is (thrown-with-msg?
          ExceptionInfo
          (Pattern/compile
            "Missing :PresentService service functions: [:missing]"
            Pattern/LITERAL)
          (sut/select-service-subschema
            {:PresentService {:present (constantly nil)}}
            {:PresentService #{:present :missing}})))))

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
          (Pattern/compile "Missing keys: [:c :d]" Pattern/LITERAL)
          (sut/select-all-keys {:a s/Any :b s/Any} [:a :b :c :d])))))

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

(deftest open-service-schema-test
  (is (= {(s/pred simple-keyword?) {(s/pred simple-keyword?) (s/pred ifn?)}}
         (sut/open-service-schema {})))
  (is (= {:ExampleService {:example-fn (s/=> s/Any)
                           (s/pred simple-keyword?) (s/pred ifn?)}
          (s/pred simple-keyword?) {(s/pred simple-keyword?) (s/pred ifn?)}}
         (sut/open-service-schema {:ExampleService {:example-fn (s/=> s/Any)}}))))
