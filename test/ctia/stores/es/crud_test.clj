(ns ctia.stores.es.crud-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.instant :as inst]
            [clojure.string :as string]
            [clojure.test :as t
             :refer
             [are deftest is testing use-fixtures]]
            [ctia.entity.sighting.schemas :as ss]
            [ctia.flows.crud :refer [gen-random-uuid]]
            [ctia.stores.es.crud :as sut]
            [ctia.stores.es.init :as init]
            [ctia.stores.es.query :as es.query]
            [ctia.task.rollover :refer [rollover-store]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :as es-helpers]
            [ctim.examples.sightings :refer [sighting-minimal sighting-maximal]]
            [ductile.index :as es-index]
            [schema.core :as s]))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each
  es-helpers/fixture-properties:es-store)

(deftest refine-full-text-query-parts-test
  (let [es-conn-state {:props {:entity :incident}
                       :services
                       {:FeaturesService
                        {:flag-value (constantly nil)
                         :entities (constantly {:incident {}})}}}
        with-def-op (assoc-in es-conn-state [:props :default_operator] "and")]
    (testing "refine-full-text-query-parts with different queries"
      (are [queries exp]
           (is (= exp (es.query/refine-full-text-query-parts
                       es-conn-state queries)))
        [{:query "foo"}] [{:query_string {:query "foo"}}]
        [{:query "foo" :query_mode :simple_query_string}] [{:simple_query_string {:query "foo"}}]

        [{:query "foo" :query_mode :simple_query_string}
         {:query "bar"}] [{:simple_query_string {:query "foo"}}
                          {:query_string {:query "bar"}}]))
    (testing "refine-full-text-query-parts schema"
      (s/with-fn-validation
        (is (thrown-with-msg?
             Exception #"does not match schema"
             (es.query/refine-full-text-query-parts
              {} [{:query "foo" :query_mode :unknown}])))
        (is (thrown-with-msg?
             Exception #"does not match schema"
             (es.query/refine-full-text-query-parts
              {} [{}])))))
    (testing "refine-full-text-query-parts default operator"
      (is (= [{:query_string {:query "foo" :default_operator "and"}}]
             (es.query/refine-full-text-query-parts
              with-def-op [{:query "foo"}])))
      (is (= [{:multi_match {:query "foo"}}]
             (es.query/refine-full-text-query-parts
              with-def-op
              [{:query "foo" :query_mode :multi_match}]))
          "no default_operator with multi_match"))
    (testing "refine-full-text-query-parts with fields"
      (is (= [{:query_string {:query "foo"
                              :default_operator "and"
                              :fields ["title" "description"]}}]
             (es.query/refine-full-text-query-parts
              with-def-op
              [{:query "foo" :fields ["title" "description"]}]))))))

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

(deftest bulk-schema-test
  (testing "bulk-schema shall generate a proper bulk schema"
    (let [partial-sighting {:title "new title"
                            :observables [{:type "ip" :value "8.8.8.8"}]}
          schema (sut/bulk-schema ss/Sighting)
          check-fn (fn [input valid?]
                     (is (= valid?
                            (nil? (s/check schema input)))))
          ok-inputs [{:create [sighting-minimal]}
                     {:update [partial-sighting]}
                     {:index [sighting-minimal]}
                     {:delete ["id1" "id2" "id3"]}
                     {:create [sighting-minimal]
                      :update [partial-sighting sighting-maximal]
                      :index [sighting-minimal sighting-maximal]
                      :delete ["id1" "id2" "id3"]}]
          ko-inputs [{:index [partial-sighting]}
                     {:delete [1 2 3]}
                     {:delete [sighting-minimal]}]]
      (doseq [tested ok-inputs]
        (check-fn tested true))
      (doseq [tested ko-inputs]
        (check-fn tested false)))))

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

(defn get-conn-state
  [app store-kw]
  (-> (helpers/get-store app store-kw)
      :state
      (update :props assoc :default_operator "AND")))

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

(deftest make-search-query-test
  (es-helpers/for-each-es-version
      "make-search-query shall build a proper query from given query string, filter map and date range"
      [5 7]
    #(es-index/delete! % "ctia_*")
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [es-conn-state (get-conn-state app :sighting)
             simple-access-ctrl-query {:terms {"groups" (:groups ident)}}]
       (with-redefs [es.query/find-restriction-query-part (constantly simple-access-ctrl-query)]
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
                                         {:full-text [{:query query-string}]}
                                         ident)))
           (is (= {:bool {:filter [simple-access-ctrl-query
                                   es-query-string-no-op]}}
                  (sut/make-search-query (update es-conn-state :props dissoc :default_operator)
                                         {:full-text [{:query query-string}]}
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
           (is (= {:bool {:filter [simple-access-ctrl-query
                                   {:simple_query_string {:query "*"
                                                          :default_operator "AND"}}]}}
                  (sut/make-search-query es-conn-state
                                         {:full-text [{:query      query-string
                                                       :query_mode :simple_query_string}]}
                                         ident)))
           (is (= {:bool {:filter [simple-access-ctrl-query
                                   {:multi_match {:query "*"}}]}}
                  (sut/make-search-query es-conn-state
                                         {:full-text [{:query      query-string
                                                       :query_mode :multi_match}]}
                                         ident))
               "multi_match queries don't support default_operator")
           (is (= {:bool {:filter (-> [simple-access-ctrl-query]
                                      (into es-terms)
                                      (into [es-date-range es-query-string-AND]))}}
                  (sut/make-search-query es-conn-state
                                         {:full-text [{:query query-string}]
                                          :range date-range
                                          :filter-map filter-map}
                                         ident)))
           (is (= {:bool {:filter [simple-access-ctrl-query
                                   {:simple_query_string {:query "simple"
                                                          :default_operator "AND"}}
                                   {:query_string {:query "lucene"
                                                   :default_operator "AND"}}]}}
                  (sut/make-search-query
                   es-conn-state
                   {:full-text [{:query "simple"
                                 :query_mode :simple_query_string}
                                {:query "lucene"
                                 :query_mode :query_string}]}
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
                                              :aggregate-on "groups"}}})))
  (is (= {:metric
          {:cardinality
           {:field "title.whole"
            :precision_threshold 10000}}}
         (sut/make-aggregation {:agg-type     :cardinality
                                :aggregate-on "title"}))
      "matches `sut/enumerable-fields-mapping` for a field")
  (is (= {:metric
          {:terms
           {:field "title.whole"
            :size  20
            :order {:_count :desc}}}}
         (sut/make-aggregation {:agg-type     :topn
                                :aggregate-on "title"
                                :limit        20
                                :sort_order   :desc}))
      "matches `sut/enumerable-fields-mapping` for a field"))

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
      (let [es-conn-state (get-conn-state app :sighting)
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
                  (count (:data (search-helper {:full-text [{:query query-string}]}
                                               {})))
                  (count-helper {:full-text [{:query query-string}]})))
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
                  (count (:data (search-helper {:full-text [{:query query-string}]
                                                :range date-range
                                                :filter-map filter-map}
                                               {})))

                  (count-helper {:full-text [{:query query-string}]
                                 :range date-range
                                 :filter-map filter-map}))))
         (testing "Properly handle search params"
           (let [search-page-0 (search-helper {:full-text [{:query query-string}]}
                                              {:limit 2})
                 search-page-1 (search-helper {:full-text [{:query query-string}]}
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
       (let [es-conn-state (get-conn-state app :sighting)
             _ (create-fn es-conn-state
                          search-metrics-entities
                          ident
                          {:refresh "true"})
             aggregate (fn [search-query agg-query]
                         (aggregate-fn es-conn-state search-query agg-query ident))]
         (testing "cardinality"
           (is (= 3 (aggregate {:full-text [{:query "*"}]}
                               {:agg-type :cardinality
                                :aggregate-on "confidence"})))
           (is (= 2 (aggregate {:full-text [{:query "confidence:(high OR medium)"}]}
                               {:agg-type :cardinality
                                :aggregate-on "confidence"}))
               "query filters should be properly applied"))
         (testing "histogram"
           (is (= [{:key  "2020-03-01T00:00:00.000-01:00" :value 70}
                   {:key  "2020-04-01T00:00:00.000-01:00" :value 25}]
                  (aggregate {:full-text [{:query "*"}]}
                             {:agg-type :histogram
                              :aggregate-on "created"
                              :granularity :month
                              :timezone "-01:00"})))
           (is (= [{:key timestamp-1 :value 60}
                   {:key timestamp-2 :value 20}]
                  (aggregate {:full-text [{:query "confidence:high"}]}
                             {:agg-type :histogram
                              :aggregate-on "created"
                              :granularity :month
                              :timezone "+00:00"}))
               "query filters should be properly applied")
           (is (= [{:key  "2020-04-01T00:00:00.000Z" :value 70}
                   {:key  "2020-05-01T00:00:00.000Z" :value 25}]
                  (aggregate {:full-text [{:query "*"}]}
                             {:agg-type     :histogram
                              :aggregate-on "created"
                              :granularity  :month})
                  (aggregate {:full-text [{:query "*"}]}
                             {:agg-type :histogram
                              :aggregate-on "created"
                              :granularity :month
                              :timezone "+00:00"}))
               "default timezone is UTC"))
         (testing "topn"
           (is (= [{:key "low":value 5}
                   {:key "medium" :value 10}]
                  (aggregate {:full-text [{:query "*"}]}
                             {:agg-type     :topn
                              :aggregate-on "confidence"
                              :limit        2
                              :sort_order   :asc})))
           (is (= [{:key "high" :value 80}
                   {:key "medium" :value 10}
                   {:key "low":value 5}]
                  (aggregate {:full-text [{:query "*"}]}
                             {:agg-type :topn
                              :aggregate-on "confidence"})
                  (aggregate {:full-text [{:query "*"}]}
                             {:agg-type :topn
                              :aggregate-on "confidence"
                              :limit 10
                              :sort_order :desc}))
               "default limit is 10, default sort_order is desc")
           (is (= [{:key "high" :value 80}
                   {:key "low" :value 5}]
                  (aggregate {:full-text [{:query "confidence:(high OR low)"}]}
                             {:agg-type     :topn
                              :aggregate-on "confidence"
                              :limit        10
                              :sort_order   :desc})))))))))

(deftest handle-delete-search
  (es-helpers/for-each-es-version
      "handle-delete-search shall properly apply query and params, delete matched data, and respect access control"
      [5 7]
      #(es-index/delete! % "ctia_*")
    (helpers/fixture-ctia-with-app
        (fn [app]
          (let [es-conn-state (get-conn-state app :sighting)
                ident-1 {:login "johndoe"
                         :groups ["group1"]}
                ident-2 {:login "janedoe"
                         :groups ["group2"]}
                data (map (fn [doc] (assoc doc
                                           :user (:login ident-1)
                                           :tlp  "amber"))
                          search-metrics-entities)
                check-fn (fn [{:keys [msg query matched ident deleted?]}]
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
              :query {:full-text [{:query "title1"}]
                      :filter-map {:confidence "High"}}
              :matched []
              :ident ident-2
              :deleted? false})

            (check-fn
             {:msg "matched entities must be properly deleted"
              :query {:full-text [{:query "title1"}]
                      :filter-map {:confidence "High"}}
              :matched high-t1-title1
              :ident ident-1
              :deleted? true}))))))

(deftest docs-with-indices-test
  (es-helpers/for-each-es-version
      "get-docs-with-indices (and variants) shall properly return documents for given ids"
      [5 7]
    #(es-index/delete! % "ctia_*")
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [conn-state (get-conn-state app :sighting)
             base-sighting {:tlp "green"
                            :groups ["group1"]}
             total 200
             sighting-ids (map #(str "sighting-"  %) (range total))
             mk-title #(str "title "%)
             sightings (map #(assoc base-sighting
                                    :id %
                                    :title (mk-title %))
                            sighting-ids)
             ;; drop some docs to check that we only match requested ids
             tested-sample (drop (/ total 10)
                                 (shuffle sightings))
             ;; create-docs
             _ (create-fn conn-state
                          sightings
                          ident
                          {})
             base-check (fn [{:keys [_index _type _id _source]}]
                          (is (= base-sighting (select-keys _source [:tlp :groups])))
                          (is (= _id (:id _source)))
                          (is (string/starts-with? _index "ctia_sighting"))
                          (when (= version 5)
                            (is (= _type "sighting"))))]
         (let [tested-sighting (rand-nth sightings)
               res (sut/get-doc-with-index conn-state
                                           (:id tested-sighting)
                                           {})]
           (base-check res)
           (is (= (:_source res) tested-sighting)))

         (let [tested-sample (drop (/ total 10) ;; drop some
                                   (shuffle sightings))
               res (sut/get-docs-with-indices conn-state
                                              (map :id tested-sample)
                                              {:limit total})]
           (is (= (set tested-sample)
                  (set (map :_source res))))
           (doseq [elem res]
             (base-check elem))))))))

(deftest bulk-delete-update-test
  (es-helpers/for-each-es-version
      "bulk-delete and bulk-update shall properly handle authorization and not-found errors"
      [5 7]
    nil
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [conn-state (get-conn-state app :sighting)
             base-sighting (assoc sighting-minimal :tlp "amber")
             total 10
             sighting-ids (map #(str "sighting-" %) (range total))
             sightings (map #(assoc base-sighting :id %) sighting-ids)
             with-owner (fn [{:keys [login groups]} doc]
                             (assoc doc
                                    :owner login
                                    :groups groups))
             ident1 {:login "john-doe"
                     :groups ["group1"]}
             ident2 {:login "jane-doe"
                     :groups ["group2"]}

             [docs-1 docs-2 docs-3] (partition-all (/ total 3) sightings)
             ;; amber documents created and owned
             docs1-ident1 (map (partial with-owner ident1) docs-1)
             ;; documents created but owned by someone else with amber tlp
             docs2-ident2 (map (partial with-owner ident2) docs-2)
             ;; documents not created
             docs3-ident1 (map (partial with-owner ident1) docs-3)
             bulk-update-handler (sut/bulk-update ss/Sighting)

             _ (create-fn conn-state
                       (concat docs1-ident1 docs2-ident2)
                       ident
                       {})
             to-update-docs (map #(assoc % :title "updated title")
                                 (concat docs1-ident1 docs2-ident2 docs3-ident1))
             update-res (bulk-update-handler conn-state
                                             to-update-docs
                                             ident1
                                             {})
             delete-res (sut/bulk-delete conn-state
                                         sighting-ids
                                         ident1
                                         {})
             expected-not-found (set (concat (map :id docs-3)
                                             (map :id docs-2)))
             expected-delete-result {:deleted (set (map :id docs-1))
                                     :errors {:not-found expected-not-found}}
             expected-update-result {:updated (set (map :id docs-1))
                                     :errors {:not-found expected-not-found}}]
         (is (= expected-update-result
                (-> (update-in update-res [:errors :not-found] set)
                    (update :updated set))))
         (is (= expected-delete-result
                (-> (update-in delete-res [:errors :not-found] set)
                    (update :deleted set)))))))))

(deftest with-default-sort-field-test
  (let [test-cases [{:msg "Use default hardcoded value when sort_by is not defined and no default sort is configured"
                     :expected {:sort_by "_doc,id"}
                     :es-params {}
                     :props  {}}
                    {:msg "Always use es-params sort_by when provided"
                     :expected {:sort_by "title"}
                     :es-params {:sort_by "title"}
                     :props  {:default-sort "id"}}
                    {:msg "Always use es-params sort_by when provided"
                     :expected {:sort_by "title"}
                     :es-params {:sort_by "title"}
                     :props  {}}
                    {:msg "When sort_by is not provided, use configured default-sort"
                     :expected {:sort_by "id"}
                     :es-params {}
                     :props  {:default-sort "id"}}]]
    (doseq [{:keys [msg expected es-params props]} test-cases]
      (is (= expected (sut/with-default-sort-field es-params props))
          msg))))
