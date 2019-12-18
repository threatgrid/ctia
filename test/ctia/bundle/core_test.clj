(ns ctia.bundle.core-test
  (:require [ctia.bundle.core :as sut]
            [clojure.test :as t :refer [deftest use-fixtures are is testing]]
            [ctia.test-helpers.core :as h]))

(use-fixtures :once h/fixture-properties:clean)

(deftest local-entity?-test
  (are [x y] (= x (sut/local-entity? y))
    false nil
    false "http://unknown.site/ctia/indicator/indicator-56067199-47c0-4294-8957-13d6b265bdc4"
    true "indicator-56067199-47c0-4294-8957-13d6b265bdc4"
    true "http://localhost:57254/ctia/indicator/indicator-56067199-47c0-4294-8957-13d6b265bdc4"))

(deftest clean-bundle-test
  (is (= {:b '(1 2 3) :d '(1 3)}
         (sut/clean-bundle {:a '(nil) :b '(1 2 3) :c '() :d '(1 nil 3)}))))

(deftest relationships-filters
  (testing "relationships-filters should properly add related_to filters to handle edge direction"
    (is (= {:source_ref "id"
            :target_ref "id"}
           (:one-of (sut/relationships-filters "id" {})))
        "default related-_to param is #{:source_ref :target_ref}")
    (is (= {:source_ref "id"}
           (:one-of (sut/relationships-filters "id" {:related_to [:source_ref]}))))
    (is (= {:target_ref "id"}
           (:one-of (sut/relationships-filters "id" {:related_to [:target_ref]}))))
    (is (= {:source_ref "id"
            :target_ref "id"}
           (:one-of (sut/relationships-filters "id" {:related_to [:source_ref :target_ref]})))))

  (testing "relationships-filters should properly add query filters"
    (is (= "source_ref:*malware*"
           (:query (sut/relationships-filters "id" {:source_type :malware}))))
    (is (= "target_ref:*sighting*"
           (:query (sut/relationships-filters "id" {:target_type :sighting}))))
    (is (= "target_ref:*sighting* AND source_ref:*malware*"
           (:query (sut/relationships-filters "id" {:source_type :malware
                                                    :target_type :sighting})))))

  (testing "relationships-filters should return proper fields and combine filters"
    (is (= {:one-of {:source_ref "id"}
            :query "source_ref:*malware*"}
           (sut/relationships-filters "id" {:source_type :malware
                                            :related_to [:source_ref]})))))
