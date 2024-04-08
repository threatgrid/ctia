(ns ctia.http.routes.common-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.string :as string]
            [clojure.test :refer [are is deftest testing use-fixtures]]
            [ctia.auth.capabilities :refer [all-capabilities]]
            [ctia.entity.incident :as incident :refer [incident-entity]]
            [ctia.http.routes.common :as sut]
            [ctia.schemas.utils :as csu]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [crud-wait-for-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.store :refer [test-selected-stores-with-app]]
            [ctia.test-helpers.http :refer [app->APIHandlerServices]]
            [ctim.examples.incidents :refer [new-incident-maximal]]
            [puppetlabs.trapperkeeper.app :as app]
            [schema-tools.core :as st]))

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

(deftest prep-es-fields-schema-test
  (test-selected-stores-with-app
    #{:es-store}
    (fn [app]
      (let [IncidentSearchParams (incident/IncidentSearchParams (app->APIHandlerServices app))
            enum->set (fn [enum-schema]
                        (->> enum-schema ffirst (apply hash-map) :vs))]
        (are [fields result] (is (= result
                                    (some-> (sut/prep-es-fields-schema
                                              {:StoreService {:get-store (constantly {:state {:searchable-fields fields}})}}
                                              {:search-q-params IncidentSearchParams})
                                            (st/get-in [:search_fields])
                                            enum->set))
                                 (format "when %s passed, %s expected" fields result))
          #{:foo :bar} #{"foo" "bar"}
          #{:foo}      #{"foo"}
          nil          nil)
        (testing "search-q-params shall not be modified with searchable-fields of nil or empty"
          (is (= IncidentSearchParams
                 (sut/prep-es-fields-schema
                  {:StoreService {:get-store (constantly {:state {:searchable-fields nil}})}}
                  {:search-q-params IncidentSearchParams})
                 (sut/prep-es-fields-schema
                  {:StoreService {:get-store (constantly {:state {:searchable-fields #{} }})}}
                  {:search-q-params IncidentSearchParams}))))))))

(defn- entity-schema+searchable-fields
  "Traverses through existing entities and grabs `schema` and
  `searchable-fields` (if any) for each.
  Returns a map where k/v pair is an entity key and a nested map with :schema
  and :searchable-fields."
  []
  ;; get the entities loaded in 'ctia.entity.entities
  ;; figure out the values of 'searchable-fields and 'schema
  (let [vars (ns-map 'ctia.entity.entities)
        ns-keys (->> vars keys (filter #(string/ends-with? % "-entity")))
        namespaces (->> ns-keys
                        (map #(some->> % (get vars)
                                       symbol
                                       find-var
                                       meta
                                       :ns))
                        (zipmap
                         (map (comp keyword #(string/replace % "-entity" ""))
                              ns-keys)))
        pluck (fn [ns var] (some->> var
                                    (str ns "/")
                                    symbol
                                    resolve
                                    deref))
        pluck-searchable-fields (fn [entity-key]
                                  (pluck
                                   (get namespaces entity-key)
                                   "searchable-fields"))
        pluck-schema (fn [entity-key]
                       (-> (get namespaces entity-key)
                           (pluck (str (name entity-key) "-entity"))
                           :schema))]
    (zipmap
     (keys namespaces)
     (map
      #(hash-map :searchable-fields (pluck-searchable-fields %)
                 :schema (pluck-schema %))
      (keys namespaces)))))

(deftest searchable-fields-test
  (testing "make sure :searchable-fields of an entity always points to existing key in the schema"
    (doseq [[entity {:keys [searchable-fields schema]}] (entity-schema+searchable-fields)]
      (when (seq searchable-fields)
        (doseq [sf   searchable-fields
                :let [path (->> (string/split (name sf) #"\.")
                                (map keyword))]]
          (is (csu/contains-key? schema path)
              (format "%s contains %s key" entity (name sf))))))))

(def http-show-services
  {:ConfigService {:get-in-config
                   #(get-in (assoc-in {}
                                      [:ctia :http :show]
                                      {:protocol "http"
                                       :hostname "localhost"
                                       :port 3000})
                            %)}
   :CTIAHTTPServerService {:get-port (constantly 443)}})

(deftest search-query-test
  (with-redefs [sut/now (constantly #inst "2020-12-31")]
    (let [from #inst "2020-04-01"
          to #inst "2020-06-01"]
      (is (= {:full-text [{:query "bad-domain", :query_mode :query_string}]}
             (sut/search-query {:date-field :created 
                                :params {:query "bad-domain"}}
                               http-show-services)))
      (is (= {:range {:created
                      {:gte from
                       :lt  to}}}
             (sut/search-query {:date-field :created
                                :params {:from from
                                         :to to}}
                               http-show-services)))
      (is (= {:range {:timestamp
                      {:gte from
                       :lt  to}}}
             (sut/search-query {:date-field :timestamp
                                :params {:from from, :to to}}
                               http-show-services)))
      (is (= {:range {:created
                      {:lt to}}}
             (sut/search-query {:date-field :created
                                :params {:to to}}
                               http-show-services)))
      (is (= {:range {:created
                      {:gte from}}}
             (sut/search-query {:date-field :created
                                :params {:from from}}
                               http-show-services)))
      (is (= {:filter-map {:title "firefox exploit"
                           :disposition 2}}
             (sut/search-query {:date-field :created
                                :params {:title "firefox exploit", :disposition 2}}
                               http-show-services)))
      (is (= {:full-text [{:query "bad-domain", :query_mode :query_string}]
              :filter-map {:title "firefox exploit"
                           :disposition 2}}
             (sut/search-query {:date-field :created
                                :params {:query "bad-domain"
                                         :disposition 2
                                         :title "firefox exploit"}}
                               http-show-services)))
      (is (= {:full-text [{:query "bad-domain", :query_mode :query_string}]
              :filter-map {:title "firefox exploit"
                           :disposition 2}}
             (sut/search-query {:date-field :created
                                :params {:query       "bad-domain"
                                         :disposition 2
                                         :title       "firefox exploit"
                                         :fields      ["title"]
                                         :sort_by     "disposition"
                                         :sort_order  :desc}}
                               http-show-services)))
      (is (= {:full-text [{:query "bad-domain", :query_mode :query_string}]
              :range {:created {:gte from, :lt to}}
              :filter-map {:title "firefox exploit"
                           :disposition 2}}
             (sut/search-query {:date-field :created
                                :params {:query       "bad-domain"
                                         :from        from
                                         :to          to
                                         :disposition 2
                                         :title       "firefox exploit"
                                         :fields      ["title"]
                                         :sort_by     "disposition"
                                         :sort_order  :desc}}
                               http-show-services)))
      (is (= {:full-text [{:query      "lucene"
                           :query_mode :query_string
                           :fields     ["title"]}
                          {:query      "simple"
                           :query_mode :simple_query_string
                           :fields     ["title"]}]
              :filter-map {:title "firefox exploit"
                           :disposition 2}}
             (sut/search-query {:date-field :created
                                :params {:query "lucene"
                                         :simple_query "simple"
                                         :disposition 2
                                         :title "firefox exploit"
                                         :search_fields ["title"]
                                         :sort_by "disposition"
                                         :sort_order :desc}}
                               http-show-services))
          "query and simple_query can be both submitted and accepted")
      (is (= {:full-text  [{:query "simple"
                            :query_mode :simple_query_string
                            :fields ["title"]}]
              :filter-map {:title       "firefox exploit"
                           :disposition 2}}
             (sut/search-query {:date-field :created
                                :params {:simple_query  "simple"
                                         :disposition   2
                                         :title         "firefox exploit"
                                         :search_fields ["title"]
                                         :sort_by       "disposition"
                                         :sort_order    :desc}}
                               http-show-services))
          "simple_query can be the only full text search")
      (testing "make-date-range-fn should be properly called"
        (is (= {:range {:timestamp
                        {:gte #inst "2050-01-01"
                         :lt  #inst "2100-01-01"}}}
               (sut/search-query {:date-field :timestamp
                                  :params {:from from, :to to}
                                  :make-date-range-fn
                                  (fn [from to]
                                    {:gte #inst "2050-01-01"
                                     :lt #inst "2100-01-01"})}
                               http-show-services)))))))

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
                        :full-text [{:query "baddomain*", :query_mode :query_string}]
                        :field1 "foo/bar"
                        :field2 "value2"}}
             (sut/format-agg-result* cardinality
                                     :cardinality
                                     "observable.type"
                                     {:range
                                      {:timestamp {:gte from
                                                   :lt to}}
                                      :full-text [{:query "baddomain*"}]
                                      :filter-map {:field1 "foo/bar"
                                                   :field2 "value2"}})))
      (is (= {:data {:observable {:type cardinality}}
              :type :cardinality
              :filters {:from from
                        :to to
                        :field1 "value1"
                        :field2 "abc def"}}
             (sut/format-agg-result* cardinality
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
                        :full-text [{:query "android"
                                     :query_mode :query_string}]}}
             (sut/format-agg-result* topn
                                     :topn
                                     "status"
                                     {:range     {:timestamp {:gte from
                                                              :lt  to}}
                                      :full-text [{:query "android"}]})))
      (is (= {:data {:timestamp histogram}
              :type :histogram
              :filters {:from from
                        :to to}}
             (sut/format-agg-result* histogram
                                     :histogram
                                     "timestamp"
                                     {:range
                                      {:incident_time.closed
                                       {:gte from
                                        :lt to}}}))))
    (testing "format-agg-result returns paginated data with total-hits for response headers"
      (is (= {:data {:data {:observable {:type 5}}
                     :type :cardinality
                     :filters {:from from
                               :to to}}
              :paging {:total-hits 10}}
             (sut/format-agg-result {:data cardinality
                                     :paging {:total-hits 10}}
                                    :cardinality
                                    "observable.type"
                                    {:range
                                     {:timestamp {:gte from
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
            {:keys [entity] :as parameters} (assoc incident-entity
                                                   :app app
                                                   :example new-incident-maximal
                                                   :headers {:Authorization "45c1f5e3f05d0"})
            entity-store (get-in-config [:ctia :store entity])]
        (assert (= "es" entity-store) (pr-str entity-store))
        (crud-wait-for-test parameters)))))

(deftest rewrite-id-search-test
  (let [test-plan
        [{:expected "source_ref:\"http://localhost:3000/ctia/casebook/casebook-aa8c5f29-11dd-433e-9a82-6b560a47a2cb\""
          :query "source_ref:*casebook-aa8c5f29-11dd-433e-9a82-6b560a47a2cb"}

         {:expected "source_ref:\"http://localhost:3000/ctia/asset-properties/asset-properties-aa8c5f29-11dd-433e-9a82-6b560a47a2cb\""
          :query "source_ref:*asset-properties-aa8c5f29-11dd-433e-9a82-6b560a47a2cb"}

         {:expected "source_ref:\"http://localhost:3000/ctia/incident/incident-aa8c5f29-11dd-433e-9a82-6b560a47a2cb\" AND a:\"http://localhost:3000/ctia/casebook/casebook-aa8c5f29-11dd-433e-9a82-6b560a47a2cb\""
          :query "source_ref:*incident-aa8c5f29-11dd-433e-9a82-6b560a47a2cb AND a:*casebook-aa8c5f29-11dd-433e-9a82-6b560a47a2cb"}

         {:expected "source_ref:incident-aa8c5f29-11dd-433e-9a82-6b560a47a2cb* AND a:\"http://localhost:3000/ctia/casebook/casebook-aa8c5f29-11dd-433e-9a82-6b560a47a2cb\""
          :query "source_ref:incident-aa8c5f29-11dd-433e-9a82-6b560a47a2cb* AND a:*casebook-aa8c5f29-11dd-433e-9a82-6b560a47a2cb"}

         {:expected "source_ref:*"
          :query "source_ref:*"}

         {:expected "*"
          :query "*"}]]

    (doseq [{:keys [expected query]} test-plan]
      (is (=  expected (sut/prepare-lucene-id-search query http-show-services))))))
