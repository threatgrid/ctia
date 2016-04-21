(ns ctia.stores.es.query-test
  (:require [ctia.stores.es.query :as q]
            [clojure.test :refer [deftest is]]))

(deftest indicators-by-judgements-query-test
  (is (= {:filtered
          {:query {:match_all {}}
           :filter
           {:bool
            {:must
             {:terms
              {:judgements.judgement_id
               #{"judgement-41dae4cf-1721-4b25-a111-77cf28e8ca6d"
                 "judgement-734f0a85-f862-4abd-83e0-beda53556e29"}}}}}}}

         (q/indicators-by-judgements-query
          #{"judgement-41dae4cf-1721-4b25-a111-77cf28e8ca6d"
            "judgement-734f0a85-f862-4abd-83e0-beda53556e29"}))))

(deftest active-judgements-by-observable-query-test
  (is (= {:filtered
          {:query
           {:bool
            {:must
             [{:term {"observable.type" "ip"}}
              {:term {"observable.value" "10.0.0.1"}}]}},
           :filter
           {:bool
            {:must
             [{:range {"valid_time.start_time" {"lt" "now/d"}}}
              {:range {"valid_time.end_time" {"gt" "now/d"}}}]}}}}

         (q/active-judgements-by-observable-query
          {:type "ip" :value "10.0.0.1"}))))
