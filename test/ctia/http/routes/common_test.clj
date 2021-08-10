(ns ctia.http.routes.common-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.set :as set]
            [clojure.test :refer [are deftest is testing use-fixtures]]
            [ctia.auth.capabilities :refer [all-capabilities]]
            [ctia.entity.incident :refer [incident-entity]]
            [ctia.http.routes.common :as sut]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [crud-wait-for-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.store :refer [test-selected-stores-with-app]]
            [ctim.examples.incidents :refer [new-incident-maximal]]
            [puppetlabs.trapperkeeper.app :as app]
            [schema-tools.core :as st]
            [schema.core :as s]))

(use-fixtures :once
              mth/fixture-schema-validation
              whoami-helpers/fixture-server)

(deftest coerce-date-range
  (with-redefs [sut/now (constantly #inst "2020-12-31")]
    (let [from #inst "2020-04-01"
          to #inst "2020-06-01"]
      (is (= {:gte #inst "2019-12-31"
              :lt (sut/now)}
             (sut/coerce-date-range #inst "2019-12-30" nil)))
      (is (= {:gte #inst "2019-06-01"
              :lt to}
             (sut/coerce-date-range #inst "2019-06-01" to)))
      (is (= {:gte from
              :lt (sut/now)}
             (sut/coerce-date-range from nil)))
      (is (= {:gte from
              :lt to}
             (sut/coerce-date-range from to))))))

(deftest full-text-search-schema-functions
  (let [schema {(s/required-key :foo) s/Str
                (s/optional-key :bar) s/Str
                (s/optional-key (first sut/default-ignored-search-fields)) s/Str}]
    (testing "ctia.http.routes.common/searchable-fields"
      (is (= (apply s/enum [:foo :bar])
             (sut/searchable-fields
              {:schema schema}))
          "Includes required and optional keys and ignores default-ignored")
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"does not match schema"
           (sut/searchable-fields {}))))

    (testing "ctia.http.routes.common/prep-es-fields-schema"
      (let [enum->set (fn [enum-schema]
                        (-> (->> enum-schema ffirst (apply hash-map) :vs)))]
        (are [fields result]
            (= result
               (->
                (sut/prep-es-fields-schema
                 {:search-q-params {},
                  :searchable-fields fields})
                (st/get-in [:search_fields])
                enum->set))
          (sut/searchable-fields {:schema schema}) #{"foo" "bar"}
          (sut/searchable-fields {:schema schema :ignore [:bar]}) #{"foo"})))))

(deftest search-query-test
  (with-redefs [sut/now (constantly #inst "2020-12-31")]
    (let [from #inst "2020-04-01"
          to #inst "2020-06-01"]
      (is (= {:full-text {:query "bad-domain", :query_mode :query_string}}
             (sut/search-query :created {:query "bad-domain"})))
      (is (= {:range {:created
                      {:gte from
                       :lt  to}}}
             (sut/search-query :created {:from from
                                         :to to})))
      (is (= {:range {:timestamp
                      {:gte from
                       :lt  to}}}
             (sut/search-query :timestamp {:from from, :to to})))
      (is (= {:range {:created
                      {:lt to}}}
             (sut/search-query :created {:to to})))
      (is (= {:range {:created
                      {:gte from}}}
             (sut/search-query :created {:from from})))
      (is (= {:filter-map {:title "firefox exploit"
                           :disposition 2}}
             (sut/search-query :created {:title "firefox exploit", :disposition 2})))
      (is (= {:full-text {:query "bad-domain", :query_mode :query_string}
              :filter-map {:title "firefox exploit"
                           :disposition 2}}
             (sut/search-query :created {:query "bad-domain"
                                         :disposition 2
                                         :title "firefox exploit"})))
      (is (= {:full-text {:query "bad-domain", :query_mode :query_string}
              :filter-map {:title "firefox exploit"
                           :disposition 2}}
             (sut/search-query :created {:query       "bad-domain"
                                         :disposition 2
                                         :title       "firefox exploit"
                                         :fields      ["title"]
                                         :sort_by     "disposition"
                                         :sort_order  :desc})))
      (is (= {:full-text {:query "bad-domain", :query_mode :query_string}
              :range {:created {:gte from, :lt to}}
              :filter-map {:title "firefox exploit"
                           :disposition 2}}
             (sut/search-query :created {:query       "bad-domain"
                                         :from        from
                                         :to          to
                                         :disposition 2
                                         :title       "firefox exploit"
                                         :fields      ["title"]
                                         :sort_by     "disposition"
                                         :sort_order  :desc})))
      (testing "make-date-range-fn should be properly called"
        (is (= {:range {:timestamp
                        {:gte #inst "2050-01-01"
                         :lt  #inst "2100-01-01"}}}
                (sut/search-query :timestamp
                                  {:from from, :to to}
                                  (fn [from to]
                                    {:gte #inst "2050-01-01"
                                     :lt #inst "2100-01-01"}))))))))

(deftest format-agg-result-test
  (let [from #inst "2019-01-01"
        to #inst "2020-12-31"
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
                        :full-text {:query "baddomain*", :query_mode :query_string}
                        :field1 "foo/bar"
                        :field2 "value2"}}
             (sut/format-agg-result cardinality
                                    :cardinality
                                    "observable.type"
                                    {:range
                                     {:timestamp {:gte from
                                                  :lt to}}
                                     :full-text {:query "baddomain*"}
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
                                    {:range
                                     {:timestamp {:gte from
                                                  :lt to}}
                                     :filter-map {:field1 "value1"
                                                  :field2 "abc def"}}))))
    (testing "should properly format aggregation results and avoid nil filters"
      (is (= {:data {:status topn}
              :type :topn
              :filters {:from from
                        :to to
                        :full-text {:query      "android"
                                    :query_mode :query_string}}}
             (sut/format-agg-result topn
                                    :topn
                                    "status"
                                    {:range     {:timestamp {:gte from
                                                             :lt  to}}
                                     :full-text {:query "android"}})))
      (is (= {:data {:timestamp histogram}
              :type :histogram
              :filters {:from from
                        :to to}}
             (sut/format-agg-result histogram
                                    :histogram
                                    "timestamp"
                                    {:range
                                     {:incident_time.closed
                                      {:gte from
                                       :lt to}}}))))))

(deftest wait_for->refresh-test
  (is (= {:refresh "wait_for"} (sut/wait_for->refresh true)))
  (is (= {:refresh "false"} (sut/wait_for->refresh false)))
  (is (= {} (sut/wait_for->refresh nil))))

(deftest capabilities->description-test
  (testing "empty capabilities throws"
    (are [v] (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Missing capabilities!"
               (sut/capabilities->description v))
         nil
         #{})))

(deftest capabilities->string-test
  (testing "empty capabilities throws"
    (are [v] (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Missing capabilities!"
               (sut/capabilities->string v))
         nil
         #{})))

;; we choose incidents to test wait_for because it supports patches and
;; thus achieves full coverage of crud-wait-for-test
(deftest wait_for-test
  (test-selected-stores-with-app
    #{:es-store}
    (fn [app]
      (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
      (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
      (let [{{:keys [get-in-config]} :ConfigService} (app/service-graph app)
            {:keys [entity] :as parameters} (into incident-entity
                                                  {:app app
                                                   :example new-incident-maximal
                                                   :headers {:Authorization "45c1f5e3f05d0"}})
            entity-store (get-in-config [:ctia :store entity])]
        (assert (= "es" entity-store) (pr-str entity-store))
        (crud-wait-for-test parameters)))))
