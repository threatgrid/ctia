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
     {:secret "1234"}}
    :store
    {:es {:default {:auth {:params {:user "elastic"
                                    :pwd "ductile"}}}}}}})

(def map-with-hidden-creds
  {:ctia
   {"external-key-prefixes" "ctia-,tg-"
    "CustomerKey" "********"
    "password" "********"
    :auth
    {:static
     {:secret "********"}}
    :store
    {:es {:default {:auth {:params {:user "elastic"
                                    :pwd "********"}}}}}}})

(deftest filter-out-creds-test
  (is (= {}
         (sut/filter-out-creds {})))
  (is (= (get-in map-with-hidden-creds [:ctia :auth :static])
         (sut/filter-out-creds (get-in map-with-creds [:ctia :auth :static])))
      "filter-out-creds should hide values that could potentially have creds"))

(deftest deep-filter-out-creds-test
  (is (= map-with-hidden-creds
         (sut/deep-filter-out-creds map-with-creds))))

;; dev only
(defn safe-pprint [& xs]
  (->> xs
       (map sut/deep-filter-out-creds)
       (apply pp/pprint)))

;; dev only
(defn safe-pprint-str [& xs]
  (with-out-str (apply safe-pprint xs)))

(deftest safe-pprint-test
  (is (= (with-out-str (pp/pprint map-with-hidden-creds))
         (with-out-str
           (safe-pprint map-with-creds)))))

(deftest safe-prn-test
  (is (= (with-out-str (prn map-with-hidden-creds))
         (with-out-str
           (sut/safe-prn map-with-creds))
         (sut/safe-prn-str map-with-creds))))

(deftest update-items-test
  (is (= [2 4 7 3] (sut/update-items [1 5 6 3] inc dec inc))
      "works with vectors")
  (is (= [2 4 7] (sut/update-items '(1 5 6) inc dec inc))
      "works with lists")
  (is (= [2 6 7 8] (apply sut/update-items '(1 5 6 7) (repeat inc)))
      "works with repeat")
  (is (= [2 4] (sut/update-items '(1 5) inc dec inc))
      "doesn't care if more fns passed into")
  (is (thrown? IllegalArgumentException (sut/update-items {:foo 1} inc))
      "doesn't make sense for maps")
  (is (thrown? IllegalArgumentException (sut/update-items #{4} inc))
      "and doesn't make sense for sets either"))
