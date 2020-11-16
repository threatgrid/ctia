(ns ctia.lib.utils-test
  (:require [ctia.lib.utils :as sut]
            [clojure.pprint :as pp]
            [clojure.test :as t :refer [are deftest is testing]]))

(def map-with-creds
  {:ctia
   {"external-key-prefixes" "ctia-,tg-"
    "CustomerKey" "1234-5678"
    "password" "abcd"
    :auth
    {:static
     {:secret "1234"}}}})

(def map-with-hidden-creds
  {:ctia
   {"external-key-prefixes" "ctia-,tg-"
    "CustomerKey" "********"
    "password" "********"
    :auth
    {:static
     {:secret "********"}}}})

(deftest filter-out-creds-test
  (is (= {}
         (sut/filter-out-creds {})))
  (is (= (get-in map-with-hidden-creds [:ctia :auth :static])
         (sut/filter-out-creds (get-in map-with-creds [:ctia :auth :static])))
      "filter-out-creds should hide values that could potentially have creds"))

(deftest deep-filter-out-creds-test
  (is (= map-with-hidden-creds
         (sut/deep-filter-out-creds map-with-creds))))

(deftest safe-pprint-test
  (is (= (with-out-str (pp/pprint map-with-hidden-creds))
         (with-out-str
           (sut/safe-pprint map-with-creds)))))

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
