(ns ctia.lib.es.query-test
  (:require [ctia.lib.es.query :as q]
            [clojure.test :refer [deftest is]]))

(deftest nested-terms-test
  (is (= (q/nested-terms [[[:observable :type] "ip"]
                          [[:observable :value] "42.42.42.1"]])
         
         [{:terms {"observable.type" ["ip"]}}
          {:terms {"observable.value" ["42.42.42.1"]}}])))

(deftest filter-map->terms-query-test
  (is (= {:filtered
          {:query {:match_all {}},
           :filter
           {:bool
            {:must
             [{:terms {"observable.type" ["ip"]}}
              {:terms {"observable.value" ["10.0.0.1"]}}
              {:terms {"test" ["ok"]}}]}}}}
         
         (q/filter-map->terms-query {[:observable :type] "ip"
                                     [:observable :value] "10.0.0.1"
                                     :test "ok"}))))
