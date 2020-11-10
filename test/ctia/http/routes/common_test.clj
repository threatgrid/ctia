(ns ctia.http.routes.common-test
  (:require [ctia.http.routes.common :as sut]
            [clj-momo.lib.clj-time.coerce :as tc]
            [clj-momo.lib.clj-time.core :as t]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [is deftest testing use-fixtures]]))

(use-fixtures :once mth/fixture-schema-validation)

(deftest coerce-date-range
  (let [now (constantly (tc/from-string "2020-12-31"))
        services {:CTIATimeService {:now now}}
        from (tc/from-string "2020-04-01")
        to (tc/from-string "2020-06-01")]
    (is (= {:gte (tc/from-string "2019-12-31")
            :lt (now)}
           (sut/coerce-date-range services (tc/from-string "2019-12-30") nil)))
    (is (= {:gte (tc/from-string "2019-06-01")
            :lt to}
           (sut/coerce-date-range services (tc/from-string "2019-06-1") to)))
    (is (= {:gte from
            :lt (now)}
           (sut/coerce-date-range services from nil)))
    (is (= {:gte from
            :lt to}
           (sut/coerce-date-range services from to)))))

(deftest search-query-test
  (let [from (tc/from-string "2020-04-01")
        to (tc/from-string "2020-06-01")]
    (is (= {:query-string "bad-domain"}
           (sut/search-query :created {:query "bad-domain"})))
    (is (= {:date-range {:created
                         {:gte from
                          :lt to}}}
           (sut/search-query :created {:from from
                                       :to to})))

    (is (= {:date-range {:timestamp
                         {:gte from
                          :lt to}}}
           (sut/search-query :timestamp {:from from
                                         :to to})))
    (is (= {:date-range {:created
                         {:lt to}}}
           (sut/search-query :created {:to to})))
    (is (= {:date-range {:created
                         {:gte from}}}
           (sut/search-query :created {:from from})))
    (is (= {:filter-map {:title "firefox exploit"
                         :disposition 2}}
           (sut/search-query :created {:title "firefox exploit"
                                       :disposition 2})))
    (is (= {:query-string "bad-domain"
            :filter-map {:title "firefox exploit"
                         :disposition 2}}
           (sut/search-query :created {:query "bad-domain"
                                       :disposition 2
                                       :title "firefox exploit"})))
    (is (= {:query-string "bad-domain"
            :filter-map {:title "firefox exploit"
                         :disposition 2}}
           (sut/search-query :created {:query "bad-domain"
                                       :disposition 2
                                       :title "firefox exploit"
                                       :fields ["title"]
                                       :sort_by "disposition"
                                       :sort_order :desc})))
    (is (= {:query-string "bad-domain"
            :date-range {:created
                         {:gte from
                          :lt to}}
            :filter-map {:title "firefox exploit"
                         :disposition 2}}
           (sut/search-query :created {:query "bad-domain"
                                       :from from
                                       :to to
                                       :disposition 2
                                       :title "firefox exploit"
                                       :fields ["title"]
                                       :sort_by "disposition"
                                       :sort_order :desc})))
    (testing "make-date-range-fn should be properly called"
      (is (= {:date-range {:timestamp
                           {:gte (tc/from-string "2050-01-01")
                            :lt "2100-01-01"}}}
             (sut/search-query :timestamp
                               {:from from
                                :to to}
                               (fn [from to]
                                 {:gte (tc/from-string "2050-01-01")
                                  :lt "2100-01-01"})))))))

(deftest format-agg-result-test
  (let [from (tc/from-string "2019-01-01")
        to (tc/from-string "2020-12-31")
        cardinality 5
        topn [{:key "Open" :value 8}
              {:key "New" :value 4}
              {:key "Closed" :value 2}]
        histogram [{:key "2020-04-01" :value 3}
                   {:key "2020-04-02" :value 0}
                   {:key "2020-04-03" :value 0}
                   {:key "2020-04-04" :value 6}
                   {:key "2020-04-05" :value 5}]]
    (testing "should properly format aggregation results, nested fields and avoid nil filters."
      (is (= {:data {:observable {:type cardinality}}
              :type :cardinality
              :filters {:from from
                        :to to
                        :query-string "baddomain*"
                        :field1 "foo/bar"
                        :field2 "value2"}}
             (sut/format-agg-result cardinality
                                    :cardinality
                                    "observable.type"
                                    {:date-range
                                     {:timestamp {:gte from
                                                  :lt to}}
                                     :query-string "baddomain*"
                                     :filter-map {:field1 "foo/bar"
                                                  :field2 "value2"}})))
      (is (= {:data {:observable {:type cardinality}}
              :type :cardinality
              :filters {:from from
                        :to to
                        :field1 "value1"
                        :field2 "abc def"}}
             (sut/format-agg-result cardinality
                                    :cardinality
                                    "observable.type"
                                    {:date-range
                                     {:timestamp {:gte from
                                                  :lt to}}
                                     :filter-map {:field1 "value1"
                                                  :field2 "abc def"}}))))
    (testing "should properly format aggregation results and avoid nil filters"
      (is (= {:data {:status topn}
              :type :topn
              :filters {:from from
                        :to to
                        :query-string "android"}}
             (sut/format-agg-result topn
                                    :topn
                                    "status"
                                    {:date-range
                                     {:timestamp {:gte from
                                                  :lt to}}
                                     :query-string "android"})))
      (is (= {:data {:timestamp histogram}
              :type :histogram
              :filters {:from from
                        :to to}}
             (sut/format-agg-result histogram
                                    :histogram
                                    "timestamp"
                                    {:date-range
                                     {:incident_time.closed
                                      {:gte from
                                       :lt to}}}))))))

(deftest wait_for->refresh-test
  (is (= {:refresh "wait_for"} (sut/wait_for->refresh true)))
  (is (= {:refresh "false"} (sut/wait_for->refresh false)))
  (is (= {} (sut/wait_for->refresh nil))))
