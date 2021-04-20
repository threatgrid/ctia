(ns ctia.stores.es.crud-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.instant :as inst]
            [clojure.test :as t
             :refer
             [are deftest is testing use-fixtures]]
            [ctia.flows.crud :refer [gen-random-uuid]]
            [ctia.stores.es.crud :as sut]
            [ctia.stores.es.init :as init]
            [ctia.stores.es.query :refer [find-restriction-query-part]]
            [ctia.task.rollover :refer [rollover-store]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :as es-helpers]
            [ductile.index :as es-index]
            [schema.core :as s]))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each
  es-helpers/fixture-properties:es-store)

(deftest ensure-document-id-in-map-test
  (is (= {:id '("actor-677796fd-b5d2-46e3-b57d-4879bcca1ce7")}
         (sut/ensure-document-id-in-map
          {:id "http://localhost:3000/ctia/actor/actor-677796fd-b5d2-46e3-b57d-4879bcca1ce7"})))
  (is (= {:id '("actor-677796fd-b5d2-46e3-b57d-4879bcca1ce7")}
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

(def create-fn (sut/handle-create :sighting s/Any))
(def update-fn (sut/handle-update :sighting s/Any))
(def read-fn (sut/handle-read s/Any))
(def delete-fn (sut/handle-delete :sighting))
(def search-fn (sut/handle-query-string-search s/Any))
(def count-fn sut/handle-query-string-count)
(def aggregate-fn sut/handle-aggregate)

(def ident {:login "johndoe"
            :groups ["group1"]})
(def base-sighting {:title "a sighting text title"
                    :tlp "green"
                    :groups ["group1"]})
(defn props-aliased [app]
  {:entity :sighting
   :indexname (es-helpers/get-indexname app :sighting)
   :host "localhost"
   :port 9205
   :aliased true
   :rollover {:max_docs 3}
   :refresh "true"
   :version 5})

(defn props-not-aliased [app]
  {:entity :sighting
   :indexname (es-helpers/get-indexname app :sighting)
   :host "localhost"
   :port 9205
   :refresh "true"
   :version 5})

(deftest crud-aliased-test
  (es-helpers/for-each-es-version
   "crud operation should properly handle aliased states"
   [5 7]
   #(es-index/delete! % "ctia_*")
   (helpers/fixture-ctia-with-app
    (fn [app]
      (let [services (es-helpers/app->ESConnServices app)
            state-aliased (init/init-es-conn! (props-aliased app) services)
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
                                {})))))))))

(deftest crud-unaliased-test
  (es-helpers/for-each-es-version
      "crud operation should properly handle not aliased states"
      [5 7]
    #(es-index/delete! % "ctia_*")
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [services (es-helpers/app->ESConnServices app)
             state-not-aliased (init/init-es-conn! (props-not-aliased app) services)]
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
                               {}))))))))


(defn sighting-conn-state
  [app]
  (let [{:keys [get-store]} (helpers/get-service-map app :StoreService)]
    (-> (get-store :sighting)
        :state
        (update :props assoc :default_operator "AND"))))

(deftest make-search-query-test
  (es-helpers/for-each-es-version
      "make-search-query shall build a proper query from given query string, filter map and date range"
      [5 7]
    #(es-index/delete! % "ctia_*")
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [es-conn-state (sighting-conn-state app)
             simple-access-ctrl-query {:terms {"groups" (:groups ident)}}]
       (with-redefs [find-restriction-query-part (constantly simple-access-ctrl-query)]
         (let [query-string "*"
               es-query-string-AND {:query_string {:query query-string
                                                   :default_operator "AND"}}
               es-query-string-no-op {:query_string {:query query-string}}
               date-range {:created {:gte #inst "2020-04-01T00:00:00.000Z"
                                     :lt #inst "2020-05-01T00:00:00.000Z"}}
               es-date-range {:range date-range}
               filter-map {:disposition 2
                           :observable.type "domain"}
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
                                         {:range date-range}
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
                                          :range date-range
                                          :filter-map filter-map}
                                         ident))))))))))

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
                                :aggregate-on "observable.value"})))
  (is (= {:custom
          {:cardinality
           {:field "observable.value"
            :precision_threshold 10000}}}
         (sut/make-aggregation {:agg-type :cardinality
                                :agg-key :custom
                                :aggregate-on "observable.value"})))
  (is (= {:top-sources
          {:terms
           {:field "sources"
            :size 20
            :order {:_count :desc}}}
          :aggs {:by-month
                 {:date_histogram
                  {:field "created"
                   :interval :month
                   :time_zone "+00:00"}}
                 :aggs {:nb-orgs
                        {:cardinality
                         {:field "groups"
                          :precision_threshold 10000}}}}}
         (sut/make-aggregation {:agg-type :topn
                                :agg-key :top-sources
                                :aggregate-on "sources"
                                :limit 20
                                :sort_order :desc
                                :aggs {:agg-type :histogram
                                       :agg-key :by-month
                                       :aggregate-on "created"
                                       :granularity :month
                                       :aggs {:agg-type :cardinality
                                              :agg-key :nb-orgs
                                              :aggregate-on "groups"}}}))))

(defn generate-sightings
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

(def high-t1-title1 (generate-sightings 60
                                        "High"
                                        title1
                                        timestamp-1))
(def high-t2-title2 (generate-sightings 20
                                        "High"
                                        title2
                                        timestamp-2))
(def medium-t1-title1 (generate-sightings 10
                                          "Medium"
                                          title1
                                          timestamp-1))
(def low-t2-title2 (generate-sightings 5
                                       "Low"
                                       title2
                                       timestamp-2))

(def search-metrics-entities
  (concat high-t1-title1
          high-t2-title2
          medium-t1-title1
          low-t2-title2))

(deftest handle-query-string-search-count-test
  (es-helpers/for-each-es-version
   "handle search and count shall properly apply query and params"
   [5 7]
   #(es-index/delete! % "ctia_*")
   (helpers/fixture-ctia-with-app
    (fn [app]
      (let [es-conn-state (sighting-conn-state app)
             _ (create-fn es-conn-state
                          search-metrics-entities
                          ident
                          {:refresh "true"})
             query-string "title1"
             date-range {:created {:gte (inst/read-instant-date timestamp-1)
                                   :lt (inst/read-instant-date timestamp-2)}}
             filter-map {:confidence "High"}
             search-helper (fn [q params]
                             (search-fn es-conn-state q ident params))
             count-helper (fn [q]
                            (count-fn es-conn-state q ident))]
         (testing "Properly handle different search query options"
           (assert (pos? (count high-t1-title1)))
           (is (= (count (concat high-t1-title1
                                 medium-t1-title1))
                  (count (:data (search-helper {:query-string query-string}
                                               {})))
                  (count-helper {:query-string query-string})))
           (is (= (count (concat high-t1-title1
                                 medium-t1-title1))
                  (count (:data (search-helper {:range date-range}
                                               {})))))
           (is (= (count (concat high-t1-title1
                                 high-t2-title2))
                  (count (:data (search-helper {:filter-map filter-map}
                                               {})))
                  (count-helper {:filter-map filter-map})))
           (is (= (count high-t1-title1)
                  (count (:data (search-helper {:query-string query-string
                                                :range date-range
                                                :filter-map filter-map}
                                               {})))

                  (count-helper {:query-string query-string
                                 :range date-range
                                 :filter-map filter-map}))))
         (testing "Properly handle search params"
           (let [search-page-0 (search-helper {:query-string query-string}
                                              {:limit 2})
                 search-page-1 (search-helper {:query-string query-string}
                                              (get-in search-page-0 [:paging :next]))]
             (assert (some? (:data search-page-0)))
             (assert (some? (:data search-page-1)))
             (is (= (count (concat high-t1-title1
                                   medium-t1-title1))
                    (get-in search-page-0 [:paging :total-hits])))
             (is (= 2 (count (:data search-page-0))))
             (is (not= (:data search-page-0)
                       (:data search-page-1))))))))))

(deftest handle-aggregate-test
  (es-helpers/for-each-es-version
      "handle-aggregate shall properly apply query and params to match data and then aggregate them according to aggregation parameters."
      [5 7]
    #(es-index/delete! % "ctia_*")
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [es-conn-state (sighting-conn-state app)
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
                           :sort_order :desc}))))))))

(deftest handle-delete-search
  (es-helpers/for-each-es-version
      "handle-delete-search shall properly apply query and params, delete matched data, and respect access control"
      [5 7]
      #(es-index/delete! % "ctia_*")
    (helpers/fixture-ctia-with-app
        (fn [app]
          (let [es-conn-state (sighting-conn-state app)
                ident-1 {:login "johndoe"
                         :groups ["group1"]}
                ident-2 {:login "janedoe"
                         :groups ["group2"]}
                data (map (fn [doc] (assoc doc
                                           :user (:login ident-1)
                                           :tlp  "amber"))
                          search-metrics-entities)
                check-fn (fn [{:keys [msg query matched ident deleted?] :as params}]
                           (create-fn es-conn-state
                                      data
                                      ident-1
                                      {:refresh "true"})
                           (testing msg
                             (is (= (count matched)
                                    (sut/handle-delete-search
                                     es-conn-state
                                     query
                                     ident
                                     {}))
                                 "the number of deleted entities shall be equal to the number of matched")
                             (is (= (if deleted? 0 (count matched))
                                    (count-fn es-conn-state query ident))
                                 "only matched entities shall be deleted")))]
            (check-fn
             {:msg "queries that does not match anything shall not delete any data."
              :query {:filter-map {:title "DOES NOT MATCH ANYTHING"}}
              :matched []
              :ident ident-1
              :deleted? false})

            (check-fn
             {:msg "user can only delete the data they have access to."
              :query {:query-string "title1"
                      :filter-map {:confidence "High"}}
              :matched []
              :ident ident-2
              :deleted? false})

            (check-fn
             {:msg "matched entities must be properly deleted"
              :query {:query-string "title1"
                      :filter-map {:confidence "High"}}
              :matched high-t1-title1
              :ident ident-1
              :deleted? true}))))))
