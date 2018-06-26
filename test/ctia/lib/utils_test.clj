(ns ctia.lib.utils-test
  (:require [ctia.lib.utils :as sut]
            [clojure.test :as t :refer [deftest is testing]]))

(def map-with-creds
  {:ctia
   {"password" "abcd"
    :auth
    {:static
     {:secret "1234"}}}})

(def map-with-hidden-creds
  {:ctia
   {"password" "********"
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
  (is (= (with-out-str (clojure.pprint/pprint map-with-hidden-creds))
         (with-out-str
           (sut/safe-pprint map-with-creds)))))



