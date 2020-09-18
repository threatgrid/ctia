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


(deftest service-subgraph-test
  (is (= (sut/service-subgraph {:a {:b 1}})
         {}))
  (is (= (sut/service-subgraph
           {:a {:b 1 :c 2}
            :d {:e 3 :f 4}}
           :a [:b])
         {:a {:b 1}}))
  (is (= (sut/service-subgraph
           {:a {:b 1 :c 2}
            :d {:e 3 :f 4}}
           :a [:b]
           :d [:e])
         {:a {:b 1}
          :d {:e 3}}))
  (testing "throws on uneven args"
    (is (thrown?
          AssertionError
          (sut/service-subgraph
            {}
            :b))))
  (testing "throws when selections clobber"
    (is (thrown?
          AssertionError
          (sut/service-subgraph
            {:a {:b 1}}
            :a [:b]
            :a [:b])))))
