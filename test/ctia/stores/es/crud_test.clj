(ns ctia.stores.es.crud-test
  (:require [ctia.stores.es.crud :as sut]
            [clojure.test :as t :refer [is are testing deftest]]))

(deftest partial-results-test
  (is (= {:data [{:error "Exception"
                  :id "123"}
                 {:id "124"}]}
         (sut/partial-results
          {:es-http-res-body
           {:took 3
            :errors true
            :items [{:index
                     {:_index "website"
                      :_type "blog"
                      :_id "123"
                      :status 400
                      :error "Exception"}}
                    {:index
                     {:_index "website"
                      :_type "blog"
                      :_id "124"
                      :_version 5
                      :status 200}}]}}
          [{:_id "123"
            :id "123"}
           {:_id "124"
            :id "124"}]
          identity))))

(deftest parse-sort-by-test
  (are [sort-by expected] (is (= expected
                                 (sut/parse-sort-by sort-by)))
    "title"                         [["title"]]
    :title                          [["title"]]
    "title:ASC"                     [["title" "ASC"]]
    "title:ASC,schema_version:DESC" [["title" "ASC"]
                                     ["schema_version" "DESC"]]))

(deftest format-sort-by-test
  (are [sort-fields expected] (is (= expected
                                     (sut/format-sort-by sort-fields)))
    [["title"]]                   "title"
    [["title" "ASC"]]             "title:ASC"
    [["title" "ASC"]
     ["schema_version" "DESC"]] "title:ASC,schema_version:DESC"))

(deftest rename-sort-fields
  (are [sort_by expected_sort_by] (is (= expected_sort_by
                                         (:sort_by (sut/rename-sort-fields
                                                    {:sort_by sort_by}))))
    "title" "title.whole"
    "revision:DESC,title:ASC,schema_version:DESC" (str "revision:DESC,"
                                                       "title.whole:ASC,"
                                                       "schema_version:DESC")))
