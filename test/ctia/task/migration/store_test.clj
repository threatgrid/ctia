(ns ctia.task.migration.store-test
  (:require [clj-momo.lib.clj-time.coerce :as time-coerce]
            [clj-momo.lib.clj-time.core :as time]
            [ductile
             [conn :refer [connect]]
             [index :as ductile.index]
             [document :as es-doc]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.stores.es.mapping :as em]
            [ctia.task.migration.store :as sut]
            [ctia.task.rollover :refer [rollover-stores]]
            [ctia.test-helpers.core :as helpers :refer [GET DELETE POST-bulk PUT]]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.fixtures :as fixt]
            [ctia.test-helpers.migration :refer [app->MigrationStoreServices]]
            [ctim.domain.id :refer [long-id->id]]
            [schema.test :refer [validate-schemas]])
  (:import [java.util UUID]))

(deftest prefixed-index-test
  (is (= "v0.4.2_ctia_actor"
         (sut/prefixed-index "ctia_actor" "0.4.2")))
  (is (= "v0.4.2_ctia_actor"
         (sut/prefixed-index "v0.4.1_ctia_actor" "0.4.2"))))

(deftest target-index-config-test
  (is (= {:settings {:number_of_replicas 0
                     :refresh_interval -1}
          :aliases {"test_index" {}
                    "test_index-write" {}}}
         (sut/target-index-config "test_index"
                                  {}
                                  {:write-index "test_index-write"})))
  (is (= {:settings {:number_of_replicas 0
                     :refresh_interval -1
                     :number_of_shards 3}
          :mappings {:a :b}
          :aliases {"test_index" {}
                    "test_index-write" {}}}
         (sut/target-index-config "test_index"
                                  {:settings {:refresh_interval 2
                                              :number_of_shards 3}
                                   :mappings {:a :b}}
                                  {:write-index "test_index-write"}))))

(deftest missing-query-test
  (is (= {:bool
          {:must_not
           {:exists
            {:field :timestamp}}}}
         (sut/missing-query :timestamp)))
  (is (= {:bool
          {:must_not
           {:exists
            {:field :date}}}}
         (sut/missing-query :date))))

(deftest range-query-test
  (is (= {:bool
          {:filter
           {:range
            {:created
             {:gte "2019-03-11T00:00:00.000Z"
              :lt "2019-03-11T00:00:00.000Z||+1d"}}}}}
         (sut/range-query "2019-03-11T00:00:00.000Z" :created "d"))))

(deftest last-range-query-test
  (testing "last-range-query should produce a bool filter with a range query."
    (is (= {:bool
            {:filter
             {:range
              {:modified
               {:gte "2019-03-25T00:00:00.000Z"}}}}}
           (sut/last-range-query "2019-03-25T00:00:00.000Z"
                                 :modified
                                 false)))
    (is (= {:bool
            {:filter
             {:range
              {:modified
               {:gt "2019-03-25T00:00:00.000Z"}}}}}
           (sut/last-range-query "2019-03-25T00:00:00.000Z"
                                 :modified
                                 false
                                 true))
        "when strict? is set to false, last-range should ust :gt filter")
    (is (= (sut/last-range-query "2019-03-25T00:00:00.000Z"
                                 :modified
                                 false)
           (sut/last-range-query "2019-03-25T00:00:00.000Z"
                                 :modified
                                 false
                                 false))
        "default strict? value should be false")
    (is (= {:bool
            {:filter
             {:range
              {:modified
               {:gte 1553472000000
                :format "epoch_millis"}}}}}
           (sut/last-range-query 1553472000000
                                 :modified
                                 true))
        "When epoch_millis? is true, it should add the epoch_millis format into the range query")))

(def missing-modified-query
  {:bool
   {:must_not
    {:exists
     {:field :modified}}}})

(deftest format-buckets-test
  (let [raw-buckets-1 [{:key_as_string "2019-03-11T00:00:00.000Z"
                        :key 1552262400000
                        :doc_count 1}
                       {:key_as_string "2019-03-18T00:00:00.000Z"
                        :key 1552867200000
                        :doc_count 0}
                       {:key_as_string "2019-03-25T00:00:00.000Z"
                        :key 1553472000000
                        :doc_count 54183}
                       {:key_as_string "2019-04-01T00:00:00.000Z"
                        :key 1554076800000
                        :doc_count 0}]
        raw-buckets-2 [{:key_as_string "2019-03-25T00:00:00.000Z"
                        :key 1553472000000
                        :doc_count 54183}]
        last-query {:bool
                    {:filter
                     {:range
                      {:modified
                       {:gte "2019-03-25T00:00:00.000Z"}}}}}
        expected-by-day [missing-modified-query
                         {:bool
                          {:filter
                           {:range
                            {:modified
                             {:gte "2019-03-11T00:00:00.000Z"
                              :lt "2019-03-11T00:00:00.000Z||+1d"}}}}}
                         last-query]
        expected-by-week [missing-modified-query
                          {:bool
                           {:filter
                            {:range
                             {:modified
                              {:gte "2019-03-11T00:00:00.000Z"
                               :lt "2019-03-11T00:00:00.000Z||+1w"}}}}}
                          last-query]
        expected-by-month [missing-modified-query
                           {:bool
                            {:filter
                             {:range
                              {:modified
                               {:gte "2019-03-11T00:00:00.000Z"
                                :lt "2019-03-11T00:00:00.000Z||+1M"}}}}}
                           last-query]
        expected-only-one-range [missing-modified-query last-query]
        formatted-by-day (sut/format-buckets raw-buckets-1 :modified "day")
        formatted-by-week (sut/format-buckets raw-buckets-1 :modified "week")
        formatted-by-month (sut/format-buckets raw-buckets-1 :modified "month")
        formatted-only-one-range (sut/format-buckets raw-buckets-2 :modified "day")]
    (is (= expected-only-one-range formatted-only-one-range))
    (is (= (list missing-modified-query)
           (sut/format-buckets nil :modified "day")
           (sut/format-buckets [] :modified "day")))
    (is (= 3
           (count formatted-by-day)
           (count formatted-by-week)
           (count formatted-by-month))
        "format-range-buckets should filter buckets with 0 documents and always add missing-query")
    (is (= formatted-by-day expected-by-day)
        "format-range-buckets should properly format raw buckets per day")
    (is (= formatted-by-week expected-by-week)
        "format-range-buckets should properly format raw buckets per week")
    (is (= formatted-by-month expected-by-month)
        "format-range-buckets should properly format raw buckets per month")))

(deftest wo-storemaps-test
  (helpers/fixture-ctia-with-app
   (fn [app]
     (let [services (app->MigrationStoreServices app)
           fake-migration (sut/init-migration {:migration-id "migration-id-1"
                                               :prefix "0.0.0"
                                               :store-keys [:tool :sighting :malware]
                                               :confirm? false
                                               :migrations [:identity]
                                               :batch-size 1000
                                               :buffer-size 3
                                               :restart? false}
                                              services)
           wo-stores (sut/wo-storemaps fake-migration)]
       (is (nil? (get-in wo-stores [:source :store])))
       (is (nil? (get-in wo-stores [:target :store])))))))

(deftest rollover?-test
  (is (false? (sut/rollover? false 10 10 10))
      "rollover? should returned false when index is not aliased")
  (testing "rollover? should return true when migrated doc exceed a multiple of max_docs with a maximum of batch-size, false otherwise"
    (is (sut/rollover? true 100 10 100))
    (is (sut/rollover? true 100 10 101))
    (is (sut/rollover? true 100 10 109))
    (is (sut/rollover? true 100 10 110))
    (is (sut/rollover? true 100 10 200))
    (is (sut/rollover? true 100 10 301))
    (is (sut/rollover? true 100 10 309))
    (is (sut/rollover? true 100 10 310))
    (is (sut/rollover? true 100 10 311))
    (is (false? (sut/rollover? true 100 10 50)))
    (is (false? (sut/rollover? true 100 10 99)))
    (is (false? (sut/rollover? true 100 10 111)))
    (is (false? (sut/rollover? true 100 10 150)))
    (is (false? (sut/rollover? true 100 10 250)))
    (is (false? (sut/rollover? true 100 10 299)))
    (is (false? (sut/rollover? true 100 10 350)))))

(deftest search-real-index?
  (is (false? (sut/search-real-index? false
                                      {:created "08-15-1953"
                                       :modified "04-29-2019"})))
  (is (false? (sut/search-real-index? true
                                      {:created "08-15-1953"
                                       :modified "08-15-1953"})))
  (is (false? (sut/search-real-index? true
                                      {:created "08-15-1953"})))
  (is (true? (sut/search-real-index? true
                                     {:created "08-15-1953"
                                      :modified "04-29-2019"}))))

(deftest get-target-stores-test
  (helpers/fixture-ctia-with-app
   (fn [app]
     (let [services (app->MigrationStoreServices app)
           {:keys [tool malware]}
           (sut/get-target-stores "0.0.0" [:tool :malware] services)]
       (is (= "v0.0.0_ctia_malware" (:indexname malware)))
       (is (= "v0.0.0_ctia_tool" (:indexname tool)))
       (is (= "v0.0.0_ctia_malware-write" (get-in malware [:props :write-index])))
       (is (= "v0.0.0_ctia_tool-write" (get-in tool [:props :write-index])))))))


(use-fixtures :once
  (join-fixtures [es-helpers/fixture-properties:es-store
                  validate-schemas]))

(def fixtures-nb 100)
(def examples (fixt/bundle fixtures-nb false))

(deftest rollover-test
  (es-helpers/for-each-es-version
   "rollover should refresh write index and trigger rollover when index size is strictly bigger than max-docs"
   [5 7]
   #(ductile.index/delete! % "ctia_*")
   (helpers/with-properties*
     ["ctia.store.es.default.port" es-port
      "ctia.store.es.default.version" version
      "ctia.auth.type" "allow-all"]
     (fn []
       (helpers/fixture-ctia-with-app
        (fn [app]
          (with-open [rdr (io/reader "./test/data/indices/sample-relationships-1000.json")]
            (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
                  services (app->MigrationStoreServices app)
                  storename "ctia_relationship"
                  write-alias (str storename "-write")
                  max-docs 40
                  batch-size 4
                  storemap {:conn conn
                            :indexname storename
                            :mapping "relationship"
                            :props {:aliased true
                                    :write-index write-alias
                                    :rollover {:max_docs max-docs}}
                            :type "relationship"
                            :settings {}
                            :config {}}
                  docs-all (->> (line-seq rdr)
                                 (map es-helpers/prepare-bulk-ops)
                                 (map #(assoc % :_index write-alias)))
                  batch-sizes (repeatedly 300 #(inc (rand-int batch-size)))
                  test-fn (fn [{:keys [source-docs
                                       migrated-count
                                       current-index-size]
                                :as state}
                               nb]
                            (let [rollover? (<= max-docs (+ current-index-size nb))
                                  cat-before (es-helpers/get-cat-indices conn)
                                  indices-before (set (keys cat-before))
                                  _ (es-helpers/load-bulk conn
                                                          (take nb source-docs)
                                                          "false")
                                  res (when rollover?
                                        (sut/rollover storemap
                                                      batch-size
                                                      (+ nb migrated-count)
                                                      services))
                                  cat-after (es-helpers/get-cat-indices conn)
                                  indices-after (set (keys cat-after))
                                  total-after (reduce + (vals cat-after))]
                              (when rollover?
                                (is (true? (:rolled_over res)))
                                (is (< (count indices-before)
                                       (count indices-after)))
                                (is (= (+ nb migrated-count)
                                       total-after)))
                              (when-not rollover?
                                (is (= indices-before indices-after)))

                              (cond-> (update state :migrated-count + nb)
                                true (assoc :source-docs (drop nb source-docs))
                                rollover? (assoc :current-index-size 0)
                                (not rollover?) (update :current-index-size + nb))))]
              (reduce test-fn
                      {:source-docs docs-all
                       :migrated-count 0
                       :current-index-size 0}
                      batch-sizes)

              (is (every? #(<= % (+ max-docs batch-size))
                          (->> (es-helpers/get-cat-indices conn)
                               (keep (fn [[k v]]
                                       (when (str/starts-with? (name k) storename)
                                         v)))))
                  "All the indices should be smaller than max-docs + batch-size")))))))))

(deftest sliced-queries-test
  (es-helpers/for-each-es-version
   "Sliced-queries should properly decompose a store into time window queries for given time interval"
   [5 7]
   #(ductile.index/delete! % "*ctia_relationship*")
   (helpers/with-properties* ;; simple way to have a proper store initialization
     ["ctia.store.es.default.port" es-port
      "ctia.store.es.default.version" version]
     #(helpers/fixture-ctia-with-app
       (fn [app]
         (let [storemap {:conn conn
                         :indexname "ctia_relationship"
                         :mapping "relationship"
                         :props {:write-index "ctia_relationship"}
                         :type "relationship"
                         :settings {}
                         :config {}}
               _ (es-helpers/load-file-bulk conn "./test/data/indices/sample-relationships-1000.json")
               expected-queries [missing-modified-query
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-02-26T00:00:00.000Z",
                                      :lt "2018-02-26T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-03-05T00:00:00.000Z",
                                      :lt "2018-03-05T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-03-12T00:00:00.000Z",
                                      :lt "2018-03-12T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-03-19T00:00:00.000Z",
                                      :lt "2018-03-19T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-04-09T00:00:00.000Z",
                                      :lt "2018-04-09T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-04-16T00:00:00.000Z",
                                      :lt "2018-04-16T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-04-23T00:00:00.000Z",
                                      :lt "2018-04-23T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-04-30T00:00:00.000Z",
                                      :lt "2018-04-30T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-05-07T00:00:00.000Z",
                                      :lt "2018-05-07T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-05-14T00:00:00.000Z",
                                      :lt "2018-05-14T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-05-21T00:00:00.000Z",
                                      :lt "2018-05-21T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-06-18T00:00:00.000Z",
                                      :lt "2018-06-18T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-06-25T00:00:00.000Z",
                                      :lt "2018-06-25T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-07-02T00:00:00.000Z",
                                      :lt "2018-07-02T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-07-09T00:00:00.000Z",
                                      :lt "2018-07-09T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-07-16T00:00:00.000Z",
                                      :lt "2018-07-16T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-07-23T00:00:00.000Z",
                                      :lt "2018-07-23T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter
                                   {:range
                                    {:modified
                                     {:gte "2018-07-30T00:00:00.000Z",
                                      :lt "2018-07-30T00:00:00.000Z||+1w"}}}}}
                                 {:bool
                                  {:filter {:range {:modified {:gte "2018-08-06T00:00:00.000Z"}}}}}]]
           (is (= expected-queries
                  (sut/sliced-queries storemap nil "week")))
           (is (= [missing-modified-query
                   {:bool
                    {:filter
                     {:range
                      {:modified
                       {:gte "2018-07-16T00:00:00.000Z",
                        :lt "2018-07-16T00:00:00.000Z||+1w"}}}}}
                   {:bool
                    {:filter
                     {:range
                      {:modified
                       {:gte "2018-07-23T00:00:00.000Z",
                        :lt "2018-07-23T00:00:00.000Z||+1w"}}}}}
                   {:bool
                    {:filter
                     {:range
                      {:modified
                       {:gte "2018-07-30T00:00:00.000Z",
                        :lt "2018-07-30T00:00:00.000Z||+1w"}}}}}
                   {:bool
                    {:filter {:range {:modified {:gte "2018-08-06T00:00:00.000Z"}}}}}]
                  (sut/sliced-queries storemap
                                      [(time-coerce/to-long "2018-07-16T00:00:00.000Z")
                                       "whatever"]
                                      "week"))
               "slice-queries should take into account search_after param")))))))


(def short-id (fn [s] (-> s long-id->id :short-id)))

(deftest bulk-metas-test
  (es-helpers/for-each-es-version
   "bulk-metas prepares ES bulk data for given document ids"
   [5 7]
   #(ductile.index/delete! % "ctia_*")
   (helpers/with-properties*
     ["ctia.store.es.default.port" es-port
      "ctia.store.es.default.version" version
      "ctia.auth.type" "allow-all"]
     #(helpers/fixture-ctia-with-app
       (fn [app]
         (let [{:keys [all-stores]} (helpers/get-service-map app :StoreService)
               services (app->MigrationStoreServices app)
               ;; insert elements in different indices and check that we retrieve the right one
               sighting-store-map {:conn conn
                                   :indexname "ctia_sighting"
                                   :props {:write-index "ctia_sighting"}
                                   :mapping "sighting"
                                   :type "sighting"
                                   :settings {}
                                   :config {}}
               malware-store-map {:conn conn
                                  :indexname "ctia_malware"
                                  :props {:write-index "ctia_malware"}
                                  :mapping "malware"
                                  :type "malware"
                                  :settings {}
                                  :config {}}
               post-bulk-res-1 (POST-bulk app examples)
               {:keys [nb-errors]} (rollover-stores (all-stores))
               _ (assert (= 0 nb-errors) "could not properly trigger _rollover")
               post-bulk-res-2 (POST-bulk app examples)
               malware-ids (->> (:malwares post-bulk-res-1)
                                (map short-id)
                                (take 10))
               sighting-ids-1 (->> (:sightings post-bulk-res-1)
                                   (map short-id)
                                   (take 10))
               sighting-ids-2 (->> (:sightings post-bulk-res-2)
                                   (map short-id)
                                   (take 10))
               _ (ductile.index/refresh! conn)
               [malware-index-1 _] (->> (ductile.index/get conn "ctia_malware*")
                                        keys
                                        sort
                                        (map name))
               [sighting-index-1 sighting-index-2] (->> (ductile.index/get conn "ctia_sighting*")
                                                        keys
                                                        sort
                                                        (map name))
               bulk-metas-malware-res (sut/bulk-metas malware-store-map malware-ids services)
               bulk-metas-sighting-res-1 (sut/bulk-metas sighting-store-map sighting-ids-1 services)
               bulk-metas-sighting-res-2 (sut/bulk-metas sighting-store-map sighting-ids-2 services)]
           (testing "bulk-metas should property return _id, _type, _index from a document id"
             (doseq [[_id metas] bulk-metas-malware-res]
               (is (= _id (:_id metas)))
               (is (= "malware" (:_type metas)))
               (is (= malware-index-1 (:_index metas))))
             (doseq [[_id metas] bulk-metas-sighting-res-1]
               (is (= _id (:_id metas)))
               (is (= "sighting" (:_type metas)))
               (is (= sighting-index-1 (:_index metas))))
             (doseq [[_id metas] bulk-metas-sighting-res-2]
               (is (= _id (:_id metas)))
               (is (= "sighting" (:_type metas)))
               (is (= sighting-index-2 (:_index metas)))))))))))

(deftest prepare-docs-test
  ;; insert elements in different indices, modify some and check that we retrieve the right one
  (es-helpers/for-each-es-version
   "prepare-docs properly generates meta data for bulk ops for new and modified documents"
   [5 7]
   #(ductile.index/delete! % "ctia_*")
   (helpers/with-properties*
     ["ctia.store.es.default.port" es-port
      "ctia.store.es.default.version" version
      "ctia.auth.type" "allow-all"]
     #(helpers/fixture-ctia-with-app
       (fn [app]
         (let [{:keys [all-stores]} (helpers/get-service-map app :StoreService)
               services (app->MigrationStoreServices app)

               sighting-store-map {:conn conn
                                   :indexname "ctia_sighting"
                                   :props {:write-index "ctia_sighting-write"
                                           :aliased true}
                                   :mapping "sighting"
                                   :type "sighting"
                                   :settings {}
                                   :config {}}
               post-bulk-res-1 (POST-bulk app examples)
               {:keys [nb-errors]} (rollover-stores (all-stores))
               _ (is (= 0 nb-errors))
               post-bulk-res-2 (POST-bulk app examples)
               _ (ductile.index/refresh! conn)
               sighting-ids-1 (->> (:sightings post-bulk-res-1)
                                   (map short-id)
                                   (take 3))
               sighting-ids-2 (->> (:sightings post-bulk-res-2)
                                   (map short-id)
                                   (take 3))
               sighting-id-1 (first sighting-ids-1)
               sighting-id-2 (first sighting-ids-2)
               update-sighting (fn [sighting-id]
                                 (PUT app
                                      (format "ctia/sighting/%s" sighting-id)
                                      :body (-> (GET app
                                                     (format "ctia/sighting/%s" sighting-id))
                                                :parsed-body
                                                (assoc :description "UPDATED"))))
               _  (update-sighting sighting-id-1)
               _  (update-sighting sighting-id-2)
               _ (ductile.index/refresh! conn)
               [sighting-index-1 sighting-index-2] (->> (ductile.index/get conn "ctia_sighting*")
                                                        keys
                                                        sort
                                                        (map name))
               get-sighting (fn [index doc-id]
                              (es-doc/get-doc conn
                                              index
                                              "sighting"
                                              doc-id
                                              {}))
               sighting-docs-1 (map (partial get-sighting sighting-index-1)
                                    sighting-ids-1)
               sighting-docs-2 (map (partial get-sighting sighting-index-2)
                                    sighting-ids-2)
               sighting-docs (concat sighting-docs-1 sighting-docs-2)
               prepared-docs (sut/prepare-docs sighting-store-map sighting-docs services)]
           (testing "prepare-docs should set proper _id, _type, _index for modified and unmodified documents"
             (is (= (sort (concat [sighting-index-1 sighting-index-2]
                                  (repeat 4 "ctia_sighting-write")))
                    (sort (map :_index prepared-docs))))
             (is (= (repeat 6 "sighting")
                    (sort (map :_type prepared-docs))))
             (is (= (set (concat sighting-ids-1 sighting-ids-2))
                    (set (map :_id prepared-docs)))))))))))

(deftest store-batch-store-size-test
  (es-helpers/for-each-es-version
   "store-batch should properly write data in given store"
   [5 7]
   #(do (ductile.index/delete! % "test_index*")
        (ductile.index/delete! % "ctia_*"))
   (helpers/with-properties*
     ["ctia.store.es.default.port" es-port
      "ctia.store.es.default.version" version
      "ctia.auth.type" "allow-all"]
     #(helpers/fixture-ctia-with-app
       (fn [app]
         (let [services (app->MigrationStoreServices app)
               indexname "test_index"
               store {:conn conn
                      :indexname indexname
                      :props {:write-index indexname}
                      :mapping "test_mapping"}
               nb-docs-1 10
               nb-docs-2 20
               make-sample-doc (fn [nb v]
                                 {:id (str (UUID/randomUUID))
                                  :batch nb
                                  :value v})
               sample-docs-1 (map (partial make-sample-doc 1)
                                  (range nb-docs-1))
               sample-docs-2 (map (partial make-sample-doc 2)
                                  (range nb-docs-2))]
           (testing "store-batch and store-size"
             (sut/store-batch store sample-docs-1 services)
             (is (= 0 (sut/store-size store))
                 "store-batch shall not refresh the index")
             (ductile.index/refresh! conn indexname)
             (sut/store-batch store sample-docs-2 services)
             (is (= nb-docs-1 (sut/store-size store))
                 "store-size shall return the number of first batch docs")
             (ductile.index/refresh! conn indexname)
             (is (= (+ nb-docs-1 nb-docs-2) (sut/store-size store))
                 "store size shall return the proper number of documents after second refresh")
             (ductile.index/delete! conn indexname))))))))

(defn test-query-fetch-batch-events
  [{:keys [version] :as conn} services]
  (testing "query-fetch-batch should property fetch and sort events on timestamp"
    (let [indexname "event_index"
          event-store {:conn conn
                       :indexname indexname
                       :props {:write-index indexname}
                       :mapping "event"
                       :type "event"
                       :settings {}
                       :config {}}
          event-batch-1 (map #(hash-map :id (str (UUID/randomUUID))
                                        :batch 1
                                        :timestamp %
                                        :modified (rand-int 50))
                             (range 50))
          event-batch-2 (map #(hash-map :id (str (UUID/randomUUID))
                                        :batch 2
                                        :timestamp %
                                        :modified (rand-int 50))
                             (range 50 90))]
      (ductile.index/create! conn
                             indexname
                             {:settings {:refresh_interval -1}
                              :mappings (es-helpers/build-mappings {:id {:type "keyword"}
                                                                    :batch em/integer-type
                                                                    :timestamp em/integer-type
                                                                    :modified em/integer-type}
                                                                   :event
                                                                   version)})
      (sut/store-batch event-store event-batch-1 services)
      (sut/store-batch event-store event-batch-2 services)
      (ductile.index/refresh! conn indexname)
      (let [{fetched-no-query :data} (sut/fetch-batch event-store 80 0 nil nil)
            {fetched-batch-1 :data} (sut/query-fetch-batch {:term {:batch 1}}
                                                           event-store
                                                           80
                                                           0
                                                           nil
                                                           nil)
            {fetched-batch-2 :data} (sut/query-fetch-batch {:term {:batch 2}}
                                                           event-store
                                                           80
                                                           0
                                                           nil
                                                           nil)

            {fetched-events-1 :data} (sut/fetch-batch event-store
                                                      5
                                                      0
                                                      "asc"
                                                      nil)
            {fetched-events-2 :data :as res} (sut/fetch-batch event-store
                                                              5
                                                              5
                                                              "asc"
                                                              nil)
            search_after (get-in res [:paging :next :search_after])
            {fetched-events-3 :data} (sut/fetch-batch event-store
                                                      100
                                                      0
                                                      "asc"
                                                      search_after)
            {fetched-events-4 :data} (sut/fetch-batch event-store
                                                      100
                                                      0
                                                      "desc"
                                                      nil)]
        (is (= 80 (count fetched-no-query)) "limit was not properly handled")
        (is (= 50 (count fetched-batch-1)) "query was not propertly handled on batch 1")
        (is (= 40 (count fetched-batch-2)) "query was not propertly handled on batch 2")
        (is (= 80 (count fetched-events-3)) "search_after was not properly handled")
        (is (apply < (map :timestamp (concat fetched-events-1
                                             fetched-events-2
                                             fetched-events-3)))
            "offset and asc orders should be properly applied, events must be sorted on timestamp")
        (is (apply > (map :timestamp fetched-events-4))
            "desc sort was not properly applied")))))

(defn test-query-fetch-batch-entities
  [{:keys [version] :as conn} services]
  (testing "query-fetch-batch should properly sort entities on modified field"
    (let [tool-indexname "tool_index"
          malware-indexname "malware_index"
          tool-store {:conn conn
                      :indexname tool-indexname
                      :props {:write-index tool-indexname}
                      :mapping "tool"
                      :type "tool"
                      :settings {}
                      :config {}}
          tool-batch (map #(hash-map :id (str "tool-" %)
                                     :timestamp (rand-int 100)
                                     :modified %
                                     :created %)
                          (range 100))
          malware-store {:conn conn
                         :indexname malware-indexname
                         :mapping "malware"
                         :props {:write-index malware-indexname}
                         :type "malware"
                         :settings {}
                         :config {}}
          malware-batch (map #(hash-map :id (str "malware-" %)
                                        :timestamp (rand-int 100)
                                        :modified %
                                        :created %)
                             (range 100))
          mappings {:id {:type "keyword"}
                    :timestamp em/integer-type
                    :created em/integer-type
                    :modified em/integer-type}]
      (ductile.index/create! conn
                             tool-indexname
                             {:settings {:refresh_interval -1}
                              :mappings (es-helpers/build-mappings mappings
                                                                   :tool
                                                                   version)})
      (ductile.index/create! conn
                             malware-indexname
                             {:settings {:refresh_interval -1}
                              :mappings (es-helpers/build-mappings mappings
                                                                   :malware
                                                                   version)})
      (sut/store-batch tool-store tool-batch services)
      (sut/store-batch malware-store malware-batch services)
      (ductile.index/refresh! conn "*")
      (let [{fetched-tool-asc :data} (sut/fetch-batch tool-store 80 0 "asc" nil)
            {fetched-tool-desc :data} (sut/fetch-batch tool-store 80 0 "desc" nil)
            {fetched-malware-asc :data} (sut/fetch-batch malware-store 80 0 "asc" nil)
            {fetched-malware-desc :data} (sut/fetch-batch malware-store 80 0 "desc" nil)]
        (is (apply < (map :modified (concat fetched-tool-asc))))
        (is (apply > (map :modified (concat fetched-tool-desc))))
        (is (apply < (map :modified (concat fetched-malware-asc))))
        (is (apply > (map :modified (concat fetched-malware-desc))))))))

(deftest query-fetch-batch-test
  (es-helpers/for-each-es-version
   "query-fetch should properly fetch and sort data"
   [5 7]
   (fn [conn]
     (ductile.index/delete! conn "ctia_*")
     (ductile.index/delete! conn "event_index*")
     (ductile.index/delete! conn "tool_index*")
     (ductile.index/delete! conn "malware_index*"))
   (helpers/with-properties*
     ["ctia.store.es.default.port" es-port
      "ctia.store.es.default.version" version
      "ctia.auth.type" "allow-all"]
     #(helpers/fixture-ctia-with-app
       (fn [app]
         (let [services (app->MigrationStoreServices app)]
           (test-query-fetch-batch-events conn services)
           (test-query-fetch-batch-entities conn services)))))))

(deftest fetch-deletes-test
  (es-helpers/for-each-es-version
   "fetch-deletes should be properly configured to fetch deletes in source store"
   [5 7]
   #(ductile.index/delete! % "ctia_*")
   (helpers/with-properties*
     ["ctia.store.es.default.port" es-port
      "ctia.store.es.default.version" version
      "ctia.auth.type" "allow-all"]
     #(helpers/fixture-ctia-with-app
       (fn [app]
         (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
               services (app->MigrationStoreServices app)

               _ (POST-bulk app examples true)
               _ (ductile.index/refresh! conn) ;; ensure indices refresh
               [sighting1 sighting2] (:parsed-body (GET app
                                                        "ctia/sighting/search"
                                                        :query-params {:limit 2 :query "*"}))

               [tool1 tool2 tool3] (:parsed-body (GET app
                                                      "ctia/tool/search"
                                                      :query-params {:limit 3 :query "*"}))
               [malware1] (:parsed-body (GET app
                                             "ctia/malware/search"
                                             :query-params {:limit 1 :query "*"}))

               sighting1-id (-> sighting1 :id short-id)
               sighting2-id (-> sighting2 :id short-id)
               tool1-id (-> tool1 :id short-id)
               tool2-id (-> tool2 :id short-id)
               tool3-id (-> tool3 :id short-id)
               malware1-id (-> malware1 :id short-id)
               _ (DELETE app
                         (format "ctia/sighting/%s" sighting1-id))
               _ (ductile.index/refresh! conn)
               since (time/internal-now)
               _ (DELETE app
                         (format "ctia/sighting/%s" sighting2-id))
               _ (DELETE app
                         (format "ctia/tool/%s" tool1-id))
               _ (DELETE app
                         (format "ctia/tool/%s" tool2-id))
               _ (DELETE app
                         (format "ctia/tool/%s" tool3-id))
               _ (DELETE app
                         (format "ctia/tool/%s" malware1-id))
               event-store (sut/get-source-store :event services)
               {data1 :data paging1 :paging} (sut/fetch-deletes event-store
                                                                [:sighting :tool]
                                                                since
                                                                3
                                                                nil)
               {data2 :data paging2 :paging} (sut/fetch-deletes event-store
                                                                [:sighting :tool]
                                                                since
                                                                2
                                                                (:sort paging1))]

           (is (nil? (:next paging2)))
           (is (= #{(:id tool1) (:id tool2)} (->> (:tool data1)
                                                  (map :id)
                                                  set))
               "fetch-deletes first batch shall return tool1 and tool2 that were deleted after since")
           (is (= #{(:id sighting2)} (->> (:sighting data1)
                                          (map :id)
                                          set))
               "fetch-deletes shall return only the sighting that was deleted after since parameter")
           (is (= #{(:id tool3)} (->> (:tool data2)
                                      (map :id)
                                      set))
               "fetch-deletes second batch shall return tool3 that was deleted after since")
           (is (nil? (:sighting data2))
               "fetch-deletes shall not return any more sightings in the second batch")
           (is (nil? (:malware data1)) "fetch-deletes shall only retrieve entity types given as parameter")
           (is (nil? (:malware data2)) "fetch-deletes shall only retrieve entity types given as parameter")))))))

(deftest init-get-migration-test
  (es-helpers/for-each-es-version
   "init-migration should properly create new migration state from selected types."
   [5 7]
   #(ductile.index/delete! % "ctia_*")
   (helpers/with-properties*
     ["ctia.migration.store.es.default.port" es-port
      "ctia.migration.store.es.default.version" version
      "ctia.auth.type" "allow-all"]
     #(helpers/fixture-ctia-with-app
       (fn [app]
         (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
               services (app->MigrationStoreServices app)
               _ (POST-bulk app examples)
               _ (ductile.index/refresh! conn) ; ensure indices refresh
               prefix "0.0.0"
               entity-types [:tool :malware :relationship]
               migration-id-1 "migration-1"
               migration-id-2 "migration-2"
               base-migration-params {:prefix prefix
                                      :migrations [:identity]
                                      :store-keys entity-types
                                      :batch-size 1000
                                      :buffer-size 3
                                      :restart? false}
               fake-migration (sut/init-migration (assoc base-migration-params
                                                         :migration-id migration-id-1
                                                         :confirm? false)
                                                  services)
               real-migration-from-init (sut/init-migration (assoc base-migration-params
                                                                   :migration-id migration-id-2
                                                                   :confirm? true)
                                                            services)
               check-state (fn [{:keys [id stores]} migration-id message]
                             (testing message
                               (is (= id migration-id))
                               (is (= (set (keys stores))
                                      (set entity-types)))
                               (doseq [entity-type entity-types]
                                 (let [{:keys [source target started completed]} (get stores entity-type)
                                       created-indices (ductile.index/get conn (str (:index target) "*"))]
                                   (is (= "-1"  (-> (vals created-indices)
                                                    first
                                                    :settings
                                                    :index
                                                    :refresh_interval)))
                                   (is (= "0"  (-> (vals created-indices)
                                                   first
                                                   :settings
                                                   :index
                                                   :number_of_replicas)))
                                   (is (nil? started))
                                   (is (nil? completed))
                                   (is (= 0 (:migrated target)))
                                   (is (= fixtures-nb (:total source)))
                                   (is (nil? (:started source)))
                                   (is (nil? (:completed target)))
                                   (is (= entity-type
                                          (keyword (get-in source [:store :type]))))
                                   (is (= entity-type
                                          (keyword (get-in target [:store :type]))))
                                   (is (= (:index source)
                                          (get-in source [:store :indexname])))
                                   (is (= (:index target)
                                          (get-in target [:store :indexname])))))))]
           (check-state fake-migration
                        migration-id-1
                        "init-migration without confirmation shall return a proper migration state")
           (check-state real-migration-from-init
                        migration-id-2
                        "init-migration with confirmation shall return a propr migration state")
           (check-state (sut/get-migration migration-id-2 conn services)
                        migration-id-2
                        "init-migration shall store confirmed migration, and get-migration should be properly retrieved from store")
           (is (thrown? clojure.lang.ExceptionInfo
                        (sut/get-migration migration-id-1 conn services))
               "migration-id-1 was not confirmed it should not exist and thus get-migration must raise a proper exception")
           (testing "stored document shall not contains object stores in source and target"
             (let [{:keys [stores]} (es-doc/get-doc conn
                                                    "ctia_migration"
                                                    "migration"
                                                    migration-id-2
                                                    {})]
               (doseq [store stores]
                 (is (nil? (get-in store [:source :store])))
                 (is (nil? (get-in store [:target :store]))))))))))))

(deftest target-store-properties-test
  (let [default-es-props {:host "localhost"
                          :port 9200
                          :protocol :http
                          :indexname "ctia_default"
                          :replicas 1
                          :refresh_interval "1s"
                          :default_operator "AND"
                          :shards 5
                          :refresh false
                          :rollover {:max_docs 10000000}
                          :aliased true}


        source-custom-props {:indexname "source-indexname"
                             :shards 4}

        migration-cluster-props {:host "es7.iroh.site"
                                 :port 443
                                 :protocol :https}

        target-store-props {:indexname "custom-target-indexname"
                            :shards 4
                            :refresh_interval "10s"}
        base-es-props {:ctia {:store {:es {:default default-es-props}}}}
        with-source-props (assoc-in base-es-props
                                    [:ctia :store :es :sighting]
                                    source-custom-props)
        with-migration-cluster-props (assoc-in with-source-props
                                               [:ctia :migration :store :es :default]
                                               migration-cluster-props)]

    (testing "Target store properties generated from only source properties and a migration prefix reuse source properties and add the migration prefix to indexname property."
      (let [get-in-config (partial get-in with-source-props)]
        (is (= (assoc (into default-es-props source-custom-props)
                      :indexname "v0.0.1_source-indexname"
                      :entity :sighting)
               (sut/target-store-properties "0.0.1" :sighting get-in-config)))))

    (testing "Target store properties use the migration cluster properties when defined, and the prefix migration properties when the target indexname is not specified."
      (let [get-in-config (partial get-in with-migration-cluster-props)]
        (is (= (assoc (merge default-es-props
                             source-custom-props
                             migration-cluster-props)
                      :indexname "v0.0.1_source-indexname"
                      :entity :sighting)
               (sut/target-store-properties "0.0.1" :sighting get-in-config)))))

    (testing "Target store properties reuse source indexname when neither the prefix nor the target indexname are provided."
      (let [get-in-config (partial get-in with-migration-cluster-props)]
        (is (= (assoc (merge default-es-props
                             source-custom-props
                             migration-cluster-props)
                      :indexname "source-indexname"
                      :entity :sighting)
               (sut/target-store-properties nil :sighting get-in-config)))))

    (testing "Target store properties prioritize target ES properties, then migration default ES properties."
      (let [get-in-config (partial get-in
                                   (-> with-migration-cluster-props
                                       (assoc-in [:ctia :migration :store :es :sighting]
                                                 (dissoc target-store-props :indexname))))]
        (is (= (assoc (merge default-es-props
                             source-custom-props
                             migration-cluster-props
                             target-store-props)
                      :indexname "v0.0.1_source-indexname"
                      :entity :sighting)
               (sut/target-store-properties "0.0.1" :sighting get-in-config)))))

    (testing "Target store properties ignore prefix when a target indexname is defined in properties."
      (let [get-in-config (partial get-in
                                   (assoc-in with-migration-cluster-props
                                             [:ctia :migration :store :es :sighting]
                                             target-store-props))]
         (is (= (assoc (merge default-es-props
                             source-custom-props
                             migration-cluster-props
                             target-store-props)
                      :indexname "custom-target-indexname"
                      :entity :sighting)
               (sut/target-store-properties "0.0.1" :sighting get-in-config)))))))
