(ns ctia.stores.es.crud-test
  (:require [clojure.test :as t :refer [is are testing deftest use-fixtures join-fixtures]]
            [schema.core :as s]
            [ctia.flows.crud :refer [gen-random-uuid]]
            [clj-momo.lib.es.index :as es-index]
            [clj-momo.lib.es.conn :as es-conn]
            [ctia.stores.es.query :refer [find-restriction-query-part]]
            [ctia.stores.es.crud :as sut]
            [ctia.stores.es.init :as init]
            [ctia.task.rollover :refer [rollover-store]]
            [ctim.examples.sightings :refer [sighting-minimal]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]))

(deftest ensure-document-id-in-map-test
  (is (= {:id "actor-677796fd-b5d2-46e3-b57d-4879bcca1ce7"}
         (sut/ensure-document-id-in-map
          {:id "http://localhost:3000/ctia/actor/actor-677796fd-b5d2-46e3-b57d-4879bcca1ce7"})))
  (is (= {:id "actor-677796fd-b5d2-46e3-b57d-4879bcca1ce7"}
         (sut/ensure-document-id-in-map
          {:id "actor-677796fd-b5d2-46e3-b57d-4879bcca1ce7"})))
  (is (= {:title "title"}
         (sut/ensure-document-id-in-map
          {:title "title"}))))

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
(def search-fn (sut/handle-query-string-search :sighting s/Any))
(def aggregate-fn (sut/handle-aggregate :sighting))

(def ident {:login "johndoe"
            :groups ["group1"]})
(def base-sighting {:title "a sighting text title"
                    :tlp "green"
                    :groups ["group1"]})
(def props-aliased {:entity :sighting
                    :indexname "ctia_sighting"
                    :host "localhost"
                    :port 9200
                    :aliased true
                    :rollover {:max_docs 3}
                    :refresh "true"})

(def props-not-aliased {:entity :sighting
                        :indexname "ctia_sighting"
                        :host "localhost"
                        :port 9200
                        :refresh "true"})

(deftest crud-aliased-test
  (let [state-aliased (init/init-es-conn! props-aliased)
        count-index #(count (es-index/get (:conn state-aliased)
                                          (str (:index state-aliased) "*")))
        base-sighting {:title "a sighting text title"
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
                                        ident
                                        {}))))
      (is (true? (delete-fn state-aliased
                            "sighting-1"
                            ident
                            {}))))

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
                                        ident
                                        {}))))
      (is (= "value5"
             (:updated-field (update-fn state-aliased
                                        "sighting-5"
                                        (assoc base-sighting
                                               :updated-field
                                               "value5")
                                        ident
                                        {}))))
      (is (true? (delete-fn state-aliased
                            "sighting-2"
                            ident
                            {})))
      (is (true? (delete-fn state-aliased
                            "sighting-5"
                            ident
                            {}))))))

(deftest crud-unaliased-test
  (let [state-not-aliased (init/init-es-conn! props-not-aliased)]
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
                                        ident
                                        {}))))
      (is (true? (delete-fn state-not-aliased
                            "sighting-1"
                            ident
                            {}))))))

(deftest make-search-query-test
  (let [es-conn-state (-> (init/init-es-conn! props-not-aliased)
                          (update :props assoc :default_operator "AND"))
        simple-access-ctrl-query {:terms {"groups" (:groups ident)}}]
    (with-redefs [find-restriction-query-part (constantly simple-access-ctrl-query)]
      (let [query-string "*"
            es-query-string-AND {:query_string {:query query-string
                                                :default_operator "AND"}}
            es-query-string-no-op {:query_string {:query query-string}}
            date-range {:created {:gte "2020-04-01T00:00:00.000Z"
                                  :lt "2020-05-01T00:00:00.000Z"}}
            es-date-range {:range date-range}
            filter-map {"disposition" 2
                        "observable.type" "domain"}
            es-terms [{:terms {"disposition" (list 2)}}
                      {:terms {"observable.type" (list "domain")}}]]
        (is (= {:bool {:filter [simple-access-ctrl-query]}}
               (sut/make-search-query es-conn-state
                                      {}
                                      ident)))
        (is (= {:bool {:filter [simple-access-ctrl-query
                                es-query-string-AND]}}
               (sut/make-search-query es-conn-state
                                      {:query-string query-string}
                                      ident)))
        (is (= {:bool {:filter [simple-access-ctrl-query
                                es-query-string-no-op]}}
               (sut/make-search-query (update es-conn-state :props dissoc :default_operator)
                                      {:query-string query-string}
                                      ident)))
        (is (= {:bool {:filter [simple-access-ctrl-query
                                es-date-range]}}
               (sut/make-search-query es-conn-state
                                      {:date-range date-range}
                                      ident)))
        (is (= {:bool {:filter (into
                                [simple-access-ctrl-query]
                                es-terms)}}
               (sut/make-search-query es-conn-state
                                      {:filter-map filter-map}
                                      ident)))
        (is (= {:bool {:filter (-> [simple-access-ctrl-query]
                                   (into es-terms)
                                   (into [es-date-range es-query-string-AND]))}}
               (sut/make-search-query es-conn-state
                                      {:query-string query-string
                                       :date-range date-range
                                       :filter-map filter-map}
                                      ident)))))))

(deftest make-aggregation-test
  (is (= {:metric
          {:date_histogram
           {:field "the-date"
            :interval :week
            :time_zone "+02:00"}}}
         (sut/make-aggregation {:agg-type :histogram
                                :aggregate-on "the-date"
                                :granularity :week
                                :timezone "+02:00"})))
  (is (= {:metric
          {:terms
           {:field "disposition"
            :size 20
            :order {:_count :desc}}}}
         (sut/make-aggregation {:agg-type :topn
                                :aggregate-on "disposition"
                                :limit 20
                                :sort_order :desc})))
  (is (= {:metric
          {:cardinality
           {:field "observable.value"
            :precision_threshold 10000}}}
         (sut/make-aggregation {:agg-type :cardinality
                                :aggregate-on "observable.value"}))))

(defn generete-sightings
  [nb confidence title timestamp]
  (repeatedly nb
              #(assoc base-sighting
                      :id (gen-random-uuid)
                      :confidence confidence
                      :created timestamp
                      :title title)))

(def timestamp-1 "2020-04-01T00:00:00.000Z")
(def timestamp-2 "2020-05-01T00:00:00.000Z")
(def title1 "this is title1 sighting")
(def title2 "this is title2 sighting")

(def high-t1-title1 (generete-sightings 60
                                        "High"
                                        title1
                                        timestamp-1))
(def high-t2-title2 (generete-sightings 20
                                        "High"
                                        title2
                                        timestamp-2))
(def medium-t1-title1 (generete-sightings 10
                                          "Medium"
                                          title1
                                          timestamp-1))
(def low-t2-title2 (generete-sightings 5
                                       "Low"
                                       title2
                                       timestamp-2))

(def search-metrics-entities
  (concat high-t1-title1
          high-t2-title2
          medium-t1-title1
          low-t2-title2))

(deftest handle-query-string-search-test
  (testing "handle search shall properly apply query and params"
    (let [es-conn-state (-> (init/init-es-conn! props-not-aliased)
                            (update :props assoc :default_operator "AND"))
          _ (create-fn es-conn-state
                       search-metrics-entities
                       ident
                       {:refresh "true"})
          query-string "title1"
          date-range {:created {:gte timestamp-1
                                :lt timestamp-2}}
          filter-map {:confidence "High"}
          search (fn [q params]
                   (search-fn es-conn-state q ident params))]

      (testing "Properly handle different search query options"
        (is (= (count (concat high-t1-title1
                              medium-t1-title1))
               (count (:data (search {:query-string query-string}
                                     {})))))
        (is (= (count (concat high-t1-title1
                              medium-t1-title1))
               (count (:data (search {:date-range date-range}
                                     {})))))

        (is (= (count (concat high-t1-title1
                              high-t2-title2))
               (count (:data (search {:filter-map filter-map}
                                     {})))))

        (is (= (count high-t1-title1)
               (count (:data (search {:query-string query-string
                                      :date-range date-range
                                      :filter-map filter-map}
                                     {}))))))
      (testing "Properly handle search params"
        (let [search-page-0 (search {:query-string query-string}
                                    {:limit 2})
              search-page-1 (search {:query-string query-string}
                                    (get-in search-page-0 [:paging :next]))]
          (is (= (count (concat high-t1-title1
                                medium-t1-title1))
                 (get-in search-page-0 [:paging :total-hits])))
          (is (= 2 (count (:data search-page-0))))
          (is (not= (:data search-page-0)
                    (:data search-page-1))))))))

(deftest handle-aggregate-test
  (testing "handle-aggregate"
    (let [es-conn-state (-> (init/init-es-conn! props-not-aliased)
                            (update :props assoc :default_operator "AND"))
          _ (create-fn es-conn-state
                       search-metrics-entities
                       ident
                       {:refresh "true"})
          aggregate (fn [search-query agg-query]
                      (aggregate-fn es-conn-state search-query agg-query ident))]

      (testing "cardinality"
        (is (= 3 (aggregate {:query-string "*"}
                            {:agg-type :cardinality
                             :aggregate-on "confidence"})))
        (is (= 2 (aggregate {:query-string "confidence:(high OR medium)"}
                            {:agg-type :cardinality
                             :aggregate-on "confidence"}))
            "query filters should be properly applied"))
      (testing "histogram"
        (is (= [{:key  "2020-03-01T00:00:00.000-01:00" :value 70}
                {:key  "2020-04-01T00:00:00.000-01:00" :value 25}]
               (aggregate {:query-string "*"}
                          {:agg-type :histogram
                           :aggregate-on "created"
                           :granularity :month
                           :timezone "-01:00"})))
        (is (= [{:key timestamp-1 :value 60}
                {:key timestamp-2 :value 20}]
               (aggregate {:query-string "confidence:high"}
                          {:agg-type :histogram
                           :aggregate-on "created"
                           :granularity :month
                           :timezone "+00:00"}))
               "query filters should be properly applied")
        (is (= [{:key  "2020-04-01T00:00:00.000Z" :value 70}
                {:key  "2020-05-01T00:00:00.000Z" :value 25}]
               (aggregate {:query-string "*"}
                          {:agg-type :histogram
                           :aggregate-on "created"
                           :granularity :month})
               (aggregate {:query-string "*"}
                          {:agg-type :histogram
                           :aggregate-on "created"
                           :granularity :month
                           :timezone "+00:00"}))
            "default timezone is UTC"))
      (testing "topn"
        (is (= [{:key "low":value 5}
                {:key "medium" :value 10}]
               (aggregate {:query-string "*"}
                          {:agg-type :topn
                           :aggregate-on "confidence"
                           :limit 2
                           :sort_order :asc})))
        (is (= [{:key "high" :value 80}
                {:key "medium" :value 10}
                {:key "low":value 5}]
               (aggregate {:query-string "*"}
                          {:agg-type :topn
                           :aggregate-on "confidence"})
               (aggregate {:query-string "*"}
                          {:agg-type :topn
                           :aggregate-on "confidence"
                           :limit 10
                           :sort_order :desc}))
            "default limit is 10, default sort_order is desc")
        (is (= [{:key "high" :value 80}
                {:key "low" :value 5}])
            (aggregate {:query-string "confidence:(high OR low)"}
                       {:agg-type :topn
                        :aggregate-on "confidence"
                        :limit 10
                        :sort_order :desc}))))))
