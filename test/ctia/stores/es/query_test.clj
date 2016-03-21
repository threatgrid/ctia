(ns ctia.stores.es.query-test
  (:require [ctia.stores.es.query :as q]
            [clojure.test :refer [deftest is]]))

(deftest nested-terms-test
  (is (= (q/nested-terms [[[:observable :type] "ip"]
                          [[:observable :value] "42.42.42.1"]])

         [{:terms {"observable.type" ["ip"]}}
          {:terms {"observable.value" ["42.42.42.1"]}}])))

(deftest mk-nested-filter-test
  (is (= (q/mk-nested-filter
          {:observable [[[:observable :type] "ip"]
                        [[:observable :value] "42.42.42.1"]]})

         [{:nested
           {:path "observable"
            :query {:bool
                    {:must [{:terms {"observable.type" ["ip"]}}
                            {:terms {"observable.value" ["42.42.42.1"]}}]}}}}])))

(deftest mk-flat-filter-test
  (is (= (q/mk-flat-filter {:judgement "judgement-a71c737f-a3bc-428d-a644-1676e00a758d"})
         [{:terms {:judgement ["judgement-a71c737f-a3bc-428d-a644-1676e00a758d"]}}])))

(deftest filter-map->terms-query-test
  (is (= (q/filter-map->terms-query {[:observable :type] "ip"
                                     [:observable :value] "10.0.0.1"})
         {:filtered
          {:query {:match_all {}}
           :filter {:bool
                    {:must
                     [{:nested
                       {:path "observable"
                        :query
                        {:bool
                         {:must
                          [{:terms {"observable.type" ["ip"]}}
                           {:terms {"observable.value" ["10.0.0.1"]}}]}}}}]}}}})))

(deftest indicators-by-judgements-query-test
  (is (= (q/indicators-by-judgements-query
          #{"judgement-41dae4cf-1721-4b25-a111-77cf28e8ca6d"
            "judgement-734f0a85-f862-4abd-83e0-beda53556e29"})

         {:filtered
          {:filter
           {:nested
            {:path "judgements"
             :query
             {:bool
              {:must
               {:terms {:judgements.judgement_id
                        #{"judgement-41dae4cf-1721-4b25-a111-77cf28e8ca6d"
                          "judgement-734f0a85-f862-4abd-83e0-beda53556e29"}}}}}}}}})))

(deftest unexpired-judgements-by-observable-query-test
  (is (= (q/unexpired-judgements-by-observable-query
          {:type "ip" :value "10.0.0.1"})

         {:filtered
          {:query
           {:nested
            {:path "observable"
             :query
             {:bool
              {:must [{:term {"observable.type" "ip"}}
                      {:term {"observable.value" "10.0.0.1"}}]}}}}
           :filter {:nested
                    {:path "valid_time"
                     :query {:bool
                             {:must
                              [{:range {"valid_time.start_time" {"lt" "now/d"}}}
                               {:range {"valid_time.end_time" {"gt" "now/d"}}}]}}}}}})))
