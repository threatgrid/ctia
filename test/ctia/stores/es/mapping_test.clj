(ns ctia.stores.es.mapping-test
  (:require [ctia.stores.es.mapping :as sut]
            [clj-momo.lib.es
             [index :as index]
             [document :as doc]
             [conn :as conn]]
            [clojure.test :refer [deftest testing is]]))

(deftest mapping-test
  (let [indexname "test_mapping"
        doc-type "test_docs"
        mapping {:id sut/all_token
                 :boolean sut/_boolean
                 :float sut/_float
                 :long sut/_long
                 :integer sut/integer
                 :table sut/embedded-data-table
                 :timestamp sut/ts
                 :source sut/token
                 :token1 sut/token
                 :token2 sut/token
                 :all-token sut/all_token
                 :text sut/text
                 :all-text sut/all_token
                 :sortable-all-text sut/sortable-all-text}
        settings {:mappings {doc-type {:properties mapping}}
                  :settings sut/store-settings}
        es-conn (conn/connect {:host "localhost"
                               :port 9200})
        docs (map #(assoc {:id (str "doc" %)
                           :boolean (even? %)
                           :float (+ 100 %)
                           :long (+ 200 %)
                           :integer (+ 300 %)
                           :table {:row_count %}
                           :timestamp (format "2019-07-15T0%s:00:00.000-00:00" %)
                           :source "cisco"
                           :token1 "a lower token"
                           :token2 "an upper TOKEN"
                           :all-token "token in _all"
                           :text "this is a simpletext"
                           :all-text "this is an AllText"
                           :sortable-all-text (str "sortable" %)}
                          :_id (str "doc" %)
                          :_index indexname
                          :_type doc-type)
                  (range 3))
        [doc0 doc1 doc2] docs]
    (index/delete! es-conn indexname)
    (index/create! es-conn indexname settings)
    (doc/bulk-create-doc es-conn docs "true")
    (index/refresh! es-conn indexname)
    (testing "token should be matched with exact values, and can be directly used for aggregating and sorting on without fielddata"
      (let [search-res-doc0 (doc/search-docs es-conn
                                             indexname
                                             doc-type
                                             nil
                                             {:id "doc0"}
                                             nil)
            search-res-id-asc (doc/search-docs es-conn
                                               indexname
                                               doc-type
                                               nil
                                               nil
                                               {:sort_by "id"
                                                :sort_order "asc"})
            search-res-id-desc (doc/search-docs es-conn
                                               indexname
                                               doc-type
                                               nil
                                               nil
                                               {:sort_by "id"
                                                :sort_order "desc"})

            search-res-token1-1 (doc/search-docs es-conn
                                                 indexname
                                                 doc-type
                                                 nil
                                                 {:token1 "a lower token"}
                                                 nil)
            search-res-token1-2 (doc/search-docs es-conn
                                                 indexname
                                                 doc-type
                                                 nil
                                                 {:token1 "a lower TOKEN"}
                                                 nil)
            search-res-token1-3 (doc/search-docs es-conn
                                                 indexname
                                                 doc-type
                                                 nil
                                                 {:token1 "token"}
                                                 nil)

            search-res-token2-1 (doc/search-docs es-conn
                                                 indexname
                                                 doc-type
                                                 nil
                                                 {:token2 "an upper token"}
                                                 nil)
            search-res-token2-2 (doc/search-docs es-conn
                                                 indexname
                                                 doc-type
                                                 nil
                                                 {:token2 "an upper TOKEN"}
                                                 nil)

            search-res-all-token-1 (doc/search-docs es-conn
                                                    indexname
                                                    doc-type
                                                    {:query_string {:query "\"token in _all\""}}
                                                    nil
                                                    nil)
            search-res-all-token-2 (doc/search-docs es-conn
                                                    indexname
                                                    doc-type
                                                    {:query_string {:query "_all"}}
                                                    nil
                                                    nil)
            search-res-all-token-3 (doc/search-docs es-conn
                                                    indexname
                                                    doc-type
                                                    {:query_string {:query "upper"}}
                                                    nil
                                                    nil)]
        (is (= "doc0" (-> search-res-doc0 :data first :id)))
        (is (= 1 (-> search-res-doc0 :data count)))

        (is (= '("doc0" "doc1" "doc2")
               (->> search-res-id-asc
                    :data
                    (map :id))))
        (is (= '("doc2" "doc1" "doc0")
               (->> search-res-id-desc
                    :data
                    (map :id))))

        (is (= 3
               (-> search-res-token1-1 :data count)
               (-> search-res-token1-2 :data count)))
        (is (= search-res-token1-1 search-res-token1-2))
        (is (nil? (-> search-res-token1-3 :data seq)))

        (is (= 3
               (-> search-res-token2-1 :data count)
               (-> search-res-token2-2 :data count)))
        (is (= search-res-token2-1 search-res-token2-2))

        (is (= 3
               (-> search-res-all-token-1 :data count)
               (-> search-res-all-token-2 :data count)))
        (is (= search-res-all-token-1 search-res-all-token-2))
        (is (nil? (-> search-res-all-token-3 :data seq)))))

    (testing "text should be matched with analyzed values, and cannot be used for aggregating and sorting without a fielddata field"
      (let [search-res-text-1 (doc/search-docs es-conn
                                               indexname
                                               doc-type
                                               nil
                                               {:text "simpletext"}
                                               nil)
            search-res-text-2 (doc/search-docs es-conn
                                               indexname
                                               doc-type
                                               {:query_string {:query "text:simpletext"}}
                                               nil
                                               nil)
            search-res-text-3 (doc/search-docs es-conn
                                               indexname
                                               doc-type
                                               {:query_string {:query "simpletext"}}
                                               nil
                                               nil)
            search-res-all-text (doc/search-docs es-conn
                                                 indexname
                                                 doc-type
                                                 {:query_string {:query "alltext"}}
                                                 nil
                                                 nil)

            search-res-sortable-asc (doc/search-docs es-conn
                                                     indexname
                                                     doc-type
                                                     {:query_string {:query "alltext"}}
                                                     nil
                                                     {:sort_by "sortable-all-text.whole"
                                                      :sort_order "asc"})

            search-res-sortable-desc (doc/search-docs es-conn
                                                      indexname
                                                      doc-type
                                                      {:query_string {:query "alltext"}}
                                                      nil
                                                      {:sort_by "sortable-all-text.whole"
                                                       :sort_order "desc"})]
        (is (= 3
               (-> search-res-text-1 :data count)
               (-> search-res-text-2 :data count)
               (-> search-res-all-text :data count)))
        (is (= search-res-text-1
               search-res-text-2
               search-res-all-text))
        (is (nil? (-> search-res-text-3 :data seq)))

        (is (thrown? clojure.lang.ExceptionInfo
                     (doc/search-docs es-conn
                                      indexname
                                      doc-type
                                      {:query_string {:query "alltext"}}
                                      nil
                                      {:sort_by "sortable-all-text"
                                       :sort_order "asc"})))
        (is (= '("doc0" "doc1" "doc2")
               (->> search-res-sortable-asc
                    :data
                    (map :id))))
        (is (= '("doc2" "doc1" "doc0")
               (->> search-res-sortable-desc
                    :data
                    (map :id))))))
    (testing "ts mapping should be a date type, not in _all field and sortable"
      (let [search-res-all (doc/search-docs es-conn
                                            indexname
                                            doc-type
                                            {:query_string {:query "2019"}}
                                            nil
                                            {})
            search-res-range (doc/search-docs es-conn
                                              indexname
                                              doc-type
                                              {:range {:timestamp
                                                       {"gte" "2019-07-15T01:00:00.000-00:00"}}}
                                              nil
                                              {})
            search-res-timestamp-asc (doc/search-docs es-conn
                                                     indexname
                                                     doc-type
                                                     nil
                                                     nil
                                                     {:sort_by "timestamp"
                                                      :sort_order "asc"})

            search-res-timestamp-desc (doc/search-docs es-conn
                                                      indexname
                                                      doc-type
                                                      nil
                                                      nil
                                                      {:sort_by "timestamp"
                                                       :sort_order "desc"})]
      (is (nil? (-> search-res-all :data seq)))

      (is (= #{"doc1" "doc2"}
             (->> search-res-range
                  :data
                  (map :id)
                  set)))
      (is (= '("doc0" "doc1" "doc2")
             (->> search-res-timestamp-asc
                  :data
                  (map :id))))
      (is (= '("doc2" "doc1" "doc0")
             (->> search-res-timestamp-desc
                  :data
                  (map :id))))))


    (testing "_float should not be included in _all"
      (is (= "doc0"
             (-> (doc/search-docs es-conn
                                  indexname
                                  doc-type
                                  {"term" {"float" 100}}
                                  nil
                                  {})
                 :data
                 first
                 :id)))
      (is (nil? (-> (doc/search-docs es-conn
                                     indexname
                                     doc-type
                                     {"query_string" {"query" "100"}}
                                     nil
                                     {})
                    :data
                    seq))))
    (testing "_long should not be included in _all"
      (is (= "doc0"
             (-> (doc/search-docs es-conn
                                  indexname
                                  doc-type
                                  {"term" {"long" 200}}
                                  nil
                                  {})
                 :data
                 first
                 :id)))
      (is (nil? (-> (doc/search-docs es-conn
                                     indexname
                                     doc-type
                                     {"query_string" {"query" "200"}}
                                     nil
                                     {})
                    :data
                    seq))))
    (testing "integer should not be included in _all"
      (is (= "doc0"
             (-> (doc/search-docs es-conn
                                  indexname
                                  doc-type
                                  {"term" {"integer" 300}}
                                  nil
                                  {})
                 :data
                 first
                 :id)))
      (is (nil? (-> (doc/search-docs es-conn
                                     indexname
                                     doc-type
                                     {"query_string" {"query" "300"}}
                                     nil
                                     {})
                    :data
                    seq))))
    (testing "boolean should not be included in _all"
      (is (= "doc1"
             (-> (doc/search-docs es-conn
                                  indexname
                                  doc-type
                                  {"term" {"boolean" false}}
                                  nil
                                  {})
                 :data
                 first
                 :id)))
      (is (nil? (-> (doc/search-docs es-conn
                                     indexname
                                     doc-type
                                     {"query_string" {"query" "false OR true"}}
                                     nil
                                     {})
                    :data
                    seq))))
    (testing "embedded data table should not be included in _all"
      (is (= "doc0"
             (-> (doc/search-docs es-conn
                                  indexname
                                  doc-type
                                  {"term" {"table.row_count" 0}}
                                  nil
                                  {})
                 :data
                 first
                 :id)))
      (is (nil? (-> (doc/search-docs es-conn
                                     indexname
                                     doc-type
                                     {"query_string" {"query" "0"}}
                                     nil
                                     {})
                    :data
                    seq))))
    (index/delete! es-conn indexname)))
