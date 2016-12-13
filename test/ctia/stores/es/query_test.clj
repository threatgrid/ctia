(ns ctia.stores.es.query-test
  (:require [clojure.test :refer [deftest is]]
            [ctia.stores.es.query :as q]))

(deftest active-judgements-by-observable-query-test
  (is (= {:bool {:filter [{:range {"valid_time.start_time" {"lt" "now/d"}}}
                          {:range {"valid_time.end_time" {"gt" "now/d"}}}
                          {:term {"observable.type" "ip"}}
                          {:term {"observable.value" "10.0.0.1"}}]}}

         (q/active-judgements-by-observable-query
          {:type "ip" :value "10.0.0.1"}))))

(deftest nested-terms-test
  (is (= (q/nested-terms [[[:observable :type] "ip"]
                          [[:observable :value] "42.42.42.1"]])

         [{:terms {"observable.type" ["ip"]}}
          {:terms {"observable.value" ["42.42.42.1"]}}])))

(deftest filter-map->terms-query-test
  (is (= {:bool
          {:filter
           {:bool
            {:must
             [{:terms {"observable.type" ["ip"]}}
              {:terms {"observable.value" ["10.0.0.1"]}}
              {:terms {"test" ["ok"]}}]}}}}

         (q/filter-map->terms-query {[:observable :type] "ip"
                                     [:observable :value] "10.0.0.1"
                                     :test "ok"}))))
