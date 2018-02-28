(ns ctia.task.migrations-test
  (:require [clojure.test :refer [deftest is]]
            [ctia.task.migrations :as sut]))

(deftest add-groups-test
  (is (= (transduce sut/add-groups conj [{}])
         [{:groups ["tenzin"]}]))
  (is (= (transduce sut/add-groups conj [{:groups []}])
         [{:groups ["tenzin"]}]))
  (is (= (transduce sut/add-groups conj [{:groups ["foo"]}])
         [{:groups ["foo"]}])))

(deftest fix-end-time-test
  (is (= (transduce sut/fix-end-time conj [{}]) [{}]))
  (is (= (transduce sut/fix-end-time conj
                    [{:valid_time
                      {:start_time "foo"}}])
         [{:valid_time
           {:start_time "foo"}}]))
  (is (= (transduce sut/fix-end-time conj
                    [{:valid_time
                      {:end_time #inst "2535-01-01T00:00:00.000-00:00"}}])
         [{:valid_time
           {:end_time #inst "2525-01-01T00:00:00.000-00:00"}}]))

  (is (= (transduce sut/fix-end-time conj
                    [{:valid_time
                      {:end_time #inst "2524-01-01T00:00:00.000-00:00"}}])
         [{:valid_time
           {:end_time #inst "2524-01-01T00:00:00.000-00:00"}}])))

(deftest pluralize-target-test
  (is (= (transduce sut/pluralize-target conj
                    [{:type "sighting"
                      :target []}]) [{:type "sighting"
                                      :targets []}])))

(deftest target-observed_time-test
  (is (= (transduce sut/target-observed_time conj
                    [{:type "sighting"
                      :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                      :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                      :target {}}])
         [{:type "sighting"
           :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2016-02-11T00:40:48.212-00:00"}
           :target {:observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                    :end_time #inst "2016-02-11T00:40:48.212-00:00"}}}]))  )
