(ns ctia.http.routes.common-test
  (:require [ctia.http.routes.common :as sut]
            [clj-momo.lib.clj-time.coerce :as tc]
            [clj-momo.lib.clj-time.core :as t]
            [clojure.test :refer [is deftest]]))

(deftest coerce-from
  (with-redefs [sut/now (constantly (tc/from-string "2020-12-31"))]
    (let [from (tc/from-string "2020-04-01")
          to (tc/from-string "2020-06-01")]
      (is (= (tc/from-string "2019-12-31")
             (sut/coerce-from (tc/from-string "2019-12-30") nil)))
      (is (= (tc/from-string "2019-06-01")
             (sut/coerce-from (tc/from-string "2019-06-1") to)))
      (is (= from
             (sut/coerce-from from nil)))
      (is (= from
             (sut/coerce-from from to))))))

(deftest search-query-test
  (with-redefs [sut/now (constantly (tc/from-string "2020-12-31"))]
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
                                         :sort_order :desc}))))))

(deftest wait_for->refresh-test
  (is (= {:refresh "wait_for"} (sut/wait_for->refresh true)))
  (is (= {:refresh "false"} (sut/wait_for->refresh false)))
  (is (= {} (sut/wait_for->refresh nil))))
