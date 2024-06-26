(ns ctia.stores.es.mapping-test
  (:require [ctia.stores.es.mapping :as sut]
            [ductile.document :as doc]
            [ductile.index :as index]
            [ctia.test-helpers.es :as es-helpers]
            [clojure.test :refer [deftest testing is]]))

(deftest mapping-test
  (es-helpers/for-each-es-version
   "mappings of different type should apply proper analyzers and tokenizers"
   [7]
   #(index/delete! % "ctia_*")
   (let [indexname "ctia_test_mapping"
         doc-type "test_docs"
         mapping {:id sut/token
                  :boolean sut/boolean-type
                  :float sut/float-type
                  :long sut/long-type
                  :integer sut/integer-type
                  :table sut/embedded-data-table
                  :timestamp sut/ts
                  :source sut/token
                  :token1 sut/token
                  :token2 sut/token
                  :text1 sut/text
                  :text2 sut/text
                  :sortable-text sut/sortable-text}
         settings {:mappings {:properties mapping}
                   :settings sut/store-settings}
         docs (map #(do {:id (str "doc" %)
                         :boolean (even? %)
                         :float (+ 100 %)
                         :long (+ 200 %)
                         :integer (+ 300 %)
                         :table {:row_count %}
                         :timestamp (format "2019-07-15T0%s:00:00.000-00:00" %)
                         :source "cisco"
                         :token1 "a lower token"
                         :token2 "an upper TOKEN"
                         :text1 "this is a first text"
                         :text2 "this is a second text"
                         :sortable-text (str "sortable" %)
                         :_id (str "doc" %)
                         :_index indexname})
                   (range 3))
         test-sort (fn [field expected-asc]
                     (is (= expected-asc
                            (->> (doc/search-docs conn
                                                  indexname
                                                  nil
                                                  nil
                                                  {:sort_by field
                                                   :sort_order "asc"})
                                 :data
                                 (map :id))))
                     (is (= (reverse expected-asc)
                            (->> (doc/search-docs conn
                                                  indexname
                                                  nil
                                                  nil
                                                  {:sort_by field
                                                   :sort_order "desc"})
                                 :data
                                 (map :id)))))
         search-raw (fn [query filters opts]
                      (doc/search-docs conn
                                       indexname
                                       query
                                       filters
                                       opts))

         search (fn [query filters opts]
                  (:data (search-raw query filters opts)))]
     (index/create! conn indexname settings)
     (doc/bulk-index-docs conn docs {:refresh "true"})
     (index/refresh! conn indexname)
     (testing "token should be matched with exact values, and can be directly used for aggregating and sorting on without fielddata"
       (let [res-doc0 (search nil
                              {:id "doc0"}
                              nil)
             res-token1-1 (search nil
                                  {:token1 "a lower token"}
                                  nil)
             res-token1-2 (search nil
                                  {:token1 "a lower TOKEN"}
                                  nil)
             res-token1-3 (search nil
                                  {:token1 "token"}
                                  nil)

             res-token2-1 (search nil
                                  {:token2 "an upper token"}
                                  nil)
             res-token2-2 (search nil
                                  {:token2 "an upper TOKEN"}
                                  nil)

             res-all-token-1 (search {:query_string {:query "\"a lower token\""}}
                                     nil
                                     nil)
             res-all-token-2 (search {:query_string {:query "\"A LOWER TOKEN\""}}
                                     nil
                                     nil)
             res-all-token-3 (search {:query_string {:query "token"}}
                                     nil
                                     nil)]
         (test-sort "id" '("doc0" "doc1" "doc2"))

         (is (= ["doc0"] (map :id res-doc0)))

         (is (= 3
                (count res-token1-1)
                (count res-token1-2)))
         (is (= res-token1-1 res-token1-2))
         (is (nil? (seq res-token1-3)))

         (is (= 3
                (count res-token2-1)
                (count res-token2-2)))
         (is (= res-token2-1 res-token2-2))

         (is (= 3
                (count res-all-token-1)
                (count res-all-token-2)))
         (is (= res-all-token-1 res-all-token-2))
         (is (nil? (seq res-all-token-3)))))

     (testing "text should be matched with analyzed values, and cannot be used for aggregating and sorting without a fielddata field"
       (let [res-text-1 (search nil
                                {:text1 "text"}
                                nil)
             res-text-2 (search {:query_string {:query "text1:\"text\""}}
                                nil
                                nil)
             res-cross-field-text (search {:query_string {:query "first second"}}
                                          nil
                                          nil)

             res-missing-text (search {:query_string {:query "missing AND text"}}
                                      nil
                                      nil)]
         (is (= 3
                (count res-text-1)
                (count res-text-2)
                (count res-cross-field-text)))
         (is (= res-text-1
                res-text-2
                res-cross-field-text))
         (is (nil? (seq res-missing-text)))

         (is (thrown? clojure.lang.ExceptionInfo
                      (search {:query_string {:query "simpletext"}}
                              nil
                              {:sort_by "sortable-text"
                               :sort_order "asc"})))
         (test-sort "sortable-text.whole" '("doc0" "doc1" "doc2"))))
     (testing "ts mapping should be a date type, not in _all field and sortable"
       (let [res-all (search {:query_string {:query "2019"}}
                             nil
                             {})
             res-range (search {:range {:timestamp
                                        {"gte" "2019-07-15T01:00:00.000-00:00"}}}
                               nil
                               {})]
         (test-sort "timestamp" '("doc0" "doc1" "doc2"))
         (is (nil? (seq res-all)))
         (is (= #{"doc1" "doc2"}
                (->> res-range
                     (map :id)
                     set)))
         (testing "epoch date shall be properly coerced in search_after"
           (let [{page-1 :data
                  {next-paging :next} :paging}
                 (search-raw {}
                             nil
                             {:sort [:timestamp]
                              :limit 1})
                 next-paging-str-epoch (update next-paging :search_after #(map str %))
                 page-2 (search {}
                                nil
                                (into next-paging {:sort [:timestamp]}))]
             (assert (seq page-2)
                     "no data were matched to properly test search_after")
             (is (not (= page-1 page-2)))
             (is (= page-2
                    (search {}
                            nil
                            (into next-paging-str-epoch {:sort [:timestamp]}))))))))
     (testing "scalar type should be properly sorted"
       (doseq [[s r] (concat
                      (map vector
                           ["float"
                            "long"
                            "integer"
                            "table.row_count"]
                           (repeat '("doc0" "doc1" "doc2")))
                      [["boolean,id" '("doc1" "doc0" "doc2")]])]
         (test-sort s r))))))
