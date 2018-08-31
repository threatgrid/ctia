(ns ctia.stores.es.crud-test
  (:require [clojure.test :as t :refer [is are testing deftest use-fixtures join-fixtures]]
            [schema.core :as s]
            [clj-momo.lib.es.index :as es-index]
            [ctia.stores.es.crud :as sut]
            [ctia.stores.es.init :as init]
            [ctia.task.rollover :refer [rollover-store]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]))

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

(use-fixtures :each
  (join-fixtures [es-helpers/fixture-properties:es-store
                  helpers/fixture-ctia
                  es-helpers/fixture-delete-store-indexes]))

(def create-fn (sut/handle-create :sighting s/Any))
(def update-fn (sut/handle-update :sighting s/Any))
(def read-fn (sut/handle-read :sighting s/Any))
(def delete-fn (sut/handle-delete :sighting s/Any))
(def ident {:groups ["group1"]})
(def base-sighting {:title "a sighting"
                    :tlp "green"
                    :groups ["group1"]})

(deftest crud-aliased-test
  (let [props-aliased {:entity :sighting
                       :indexname "ctia_sighting"
                       :host "localhost"
                       :port 9200
                       :aliased true
                       :rollover {:max_docs 3}
                       :refresh "true"}
        state-aliased (init/init-es-conn! props-aliased)
        count-index #(count (es-index/get (:conn state-aliased)
                                          (str (:index state-aliased) "*")))
        base-sighting {:title "a sighting"
                       :tlp "green"
                       :groups ["group1"]}]

    (testing "crud operation should properly handle aliased states before rollover"
      (create-fn state-aliased
                 (map #(assoc base-sighting
                              :id (str "sighting-" %))
                      (range 4))
                 ident
                 {})
      (is (= 1 (count-index)))
      (is (= '"sighting-1"
             (:id (read-fn state-aliased "sighting-1" ident {}))))
      (is (= "value1"
             (:updated-field (update-fn state-aliased
                                        "sighting-1"
                                        (assoc base-sighting
                                               :updated-field
                                               "value1")
                                        ident))))
      (is (true? (delete-fn state-aliased
                            "sighting-1"
                            ident))))

    (rollover-store state-aliased)
    (testing "crud operation should properly handle aliased states after rollover"
      (create-fn state-aliased
                 (map #(assoc base-sighting
                              :id (str "sighting-" %))
                      (range 4 7))
                 ident
                 {})
      (is (= 2 (count-index)))
      (is (= '"sighting-2"
             (:id (read-fn state-aliased "sighting-2" ident {}))))
      (is (= '"sighting-5"
             (:id (read-fn state-aliased "sighting-5" ident {}))))
      (is (= "value2"
             (:updated-field (update-fn state-aliased
                                        "sighting-2"
                                        (assoc base-sighting
                                               :updated-field
                                               "value2")
                                        ident))))
      (is (= "value5"
             (:updated-field (update-fn state-aliased
                                        "sighting-5"
                                        (assoc base-sighting
                                               :updated-field
                                               "value5")
                                        ident))))
      (is (true? (delete-fn state-aliased
                            "sighting-2"
                            ident)))
      (is (true? (delete-fn state-aliased
                            "sighting-5"
                            ident))))))


(deftest crud-unaliased-test
  (let [props-not-aliased {:entity :sighting
                           :indexname "ctia_sighting"
                           :host "localhost"
                           :port 9200
                           :refresh "true"}
        state-not-aliased (init/init-es-conn! props-not-aliased)]

    (testing "crud operation should properly handle not aliased states"
      (create-fn state-not-aliased
                 (map #(assoc base-sighting
                              :id (str "sighting-" %))
                      (range 4))
                 ident
                 {})
      (is (= '"sighting-1"
             (:id (read-fn state-not-aliased "sighting-1" ident {}))))
      (is (= "value1"
             (:updated-field (update-fn state-not-aliased
                                        "sighting-1"
                                        (assoc base-sighting
                                               :updated-field
                                               "value1")
                                        ident))))
      (is (true? (delete-fn state-not-aliased
                            "sighting-1"
                            ident))))))

