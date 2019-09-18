(ns ctia.task.migration.store-test
  (:require [clojure.test :refer [deftest is testing join-fixtures use-fixtures]]
            [clj-momo.test-helpers.core :as mth]
            [clj-momo.lib.time :as time]
            [clj-momo.lib.es
             [conn :refer [connect]]
             [document :as es-doc]
             [index :as es-index]]
            [ctim.domain.id :refer [long-id->id]]
            [ctim.examples
             [malwares :refer [malware-minimal]]
             [relationships :refer [relationship-minimal]]
             [tools :refer [tool-minimal]]]
            [ctia.properties :as props]
            [ctia.store :refer [stores]]
            [ctia.stores.es.store :refer [store->map]]
            [ctia.test-helpers
             [fixtures :as fixt]
             [core :as helpers :refer [post-bulk delete]]
             [es :as es-helpers]
             [fake-whoami-service :as whoami-helpers]]
            [ctia.task.rollover :refer [rollover-stores]]
            [ctia.task.migration.store :as sut]))

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

(deftest wo-storemaps-test
  (let [fake-migration (sut/init-migration "migration-id-1"
                                           "0.0.0"
                                           [:tool :sighting :malware]
                                           false)
        wo-stores (sut/wo-storemaps fake-migration)]
    (is (nil? (get-in wo-stores [:source :store])))
    (is (nil? (get-in wo-stores [:target :store])))))

(deftest rollover?-test
  (is (false? (sut/rollover? false 10 10 10))
      "rollover? should returned false when index is not aliased")
  (testing "rollover? should return true when migrated doc exceed a multiple of max_docs with a maximum of batch-size, false otherwise"
    (is (sut/rollover? true 100 10 101))
    (is (sut/rollover? true 100 10 109))
    (is (sut/rollover? true 100 10 110))
    (is (sut/rollover? true 100 10 301))
    (is (sut/rollover? true 100 10 309))
    (is (sut/rollover? true 100 10 310))
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



(defn apply-fixtures
  [properties fn]
  (let [fixture-fn
        (join-fixtures [#(helpers/with-properties properties (%))
                        helpers/fixture-ctia
                        es-helpers/fixture-delete-store-indexes])]
    (fixture-fn fn)))

(deftest get-target-stores-test
  (testing "something"
    (apply-fixtures
     ["ctia.store.es.default.shards" 1
      "ctia.store.es.default.replicas" 1
      "ctia.store.es.default.port" "9200"
      "ctia.store.es.default.indexname" "ctia_default"
      "ctia.store.es.default.default_operator" "AND"
      "ctia.store.es.default.aliased" true
      "ctia.store.es.default.rollover.max_docs" 50
      "ctia.store.es.malware.indexname" "ctia_malware"
      "ctia.store.es.tool.indexname" "ctia_tool"
      "ctia.store.es.tool.aliased" false
      "ctia.store.malware" "es"
      "ctia.store.tool" "es"]
     #(let [{:keys [tool malware plop]}
            (sut/get-target-stores "0.0.0" [:tool :malware :plop])]
        (println "plop")
       (is (= "v0.0.0_ctia_malware" (:indexname malware)))
       (is (= "v0.0.0_ctia_tool" (:indexname tool)))
       (is (= "v0.0.0_ctia_default" (:indexname plop))
           "target-stores should use default configuration when index is not configured")
       (is (= "v0.0.0_ctia_malware-write" (get-in malware [:props :write-index]))
           "aliased stores should write on dedicated alias")
       (is (= "v0.0.0_ctia_tool" (get-in tool [:props :write-index]))
           "unaliased store should directly write on index")
       (is (= "v0.0.0_ctia_default-write" (get-in plop [:props :write-index]))
           "target-stores should use default configuration when index is not configured")
       ))))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  whoami-helpers/fixture-server
                  whoami-helpers/fixture-reset-state
                  helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store]))

(props/init!)
(def es-props (get-in @props/properties [:ctia :store :es]))
(def es-conn (connect (:default es-props)))
(def migration-index (get-in es-props [:migration :indexname]))

(defn fixture-clean-migration [t]
  (t)
  (es-index/delete! es-conn "v0.0.0*")
  (es-index/delete! es-conn (str migration-index "*")))

(use-fixtures :each
  (join-fixtures [helpers/fixture-ctia
                  es-helpers/fixture-delete-store-indexes
                  fixture-clean-migration]))

(def fixtures-nb 100)
(def examples (fixt/bundle fixtures-nb false))


(deftest bulk-metas-test
  ;; insert elements in different indices and check that we retrieve the right one
  (let [sighting-store-map {:conn es-conn
                            :indexname "ctia_sighting"
                            :props {:write-index "ctia_sighting"}
                            :mapping "sighting"
                            :type "sighting"
                            :settings {}
                            :config {}}
        post-bulk-res-1 (post-bulk examples)
        _ (rollover-stores @stores)
        post-bulk-res-2 (post-bulk examples)
        malware-ids (->> (:malwares post-bulk-res-1)
                         (map #(-> % long-id->id :short-id))
                         (take 10))
        sighting-ids-1 (->> (:sightings post-bulk-res-1)
                             (map #(-> % long-id->id :short-id))
                             (take 10))
        sighting-ids-2 (->> (:sightings post-bulk-res-2)
                             (map #(-> % long-id->id :short-id))
                             (take 10))
        _ (es-index/refresh! es-conn)
        [malware-index-1 _] (->> (es-index/get es-conn "ctia_malware*")
                                 keys
                                 sort
                                 (map name))
        [sighting-index-1 sighting-index-2] (->> (es-index/get es-conn "ctia_sighting*")
                                keys
                                sort
                                (map name))
        bulk-metas-malware-res (sut/bulk-metas sighting-store-map malware-ids)
        bulk-metas-sighting-res-1 (sut/bulk-metas sighting-store-map sighting-ids-1)
        bulk-metas-sighting-res-2 (sut/bulk-metas sighting-store-map sighting-ids-2)]
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
          (is (= sighting-index-2 (:_index metas)))))))

(deftest store-batch-store-size-test
  (let [indexname "test_index"
        store {:conn es-conn
               :indexname indexname
               :props {:write-index indexname}
               :mapping "test_mapping"}
        nb-docs-1 10
        nb-docs-2 20
        sample-docs-1 (map #(hash-map :id (str (java.util.UUID/randomUUID))
                                      :batch 1
                                      :value %)
                           (range nb-docs-1))
        sample-docs-2 (map #(hash-map :id (str (java.util.UUID/randomUUID))
                                      :batch 2
                                      :value %)
                           (range nb-docs-2))]
    (testing "store-batch and store-size"
      (sut/store-batch store sample-docs-1)
      (is (= 0 (sut/store-size store))
          "store-batch shall not refresh the index")
      (es-index/refresh! es-conn indexname)
      (sut/store-batch store sample-docs-2)
      (is (= nb-docs-1 (sut/store-size store))
          "store-size shall return the number of first batch docs")
      (es-index/refresh! es-conn indexname)
      (is (= (+ nb-docs-1 nb-docs-2) (sut/store-size store))
          "store size shall return the proper number of documents after second refresh")
      (es-index/delete! es-conn indexname))))

(deftest query-fetch-batch-test
  (testing "query-fetch-batch should property fetch and sort events on timestamp"
    (let [indexname "test_event"
          event-store {:conn es-conn
                       :indexname indexname
                       :props {:write-index indexname}
                       :mapping "event"
                       :type "event"
                       :settings {}
                       :config {}}
          event-batch-1 (map #(hash-map :id (str (java.util.UUID/randomUUID))
                                        :batch 1
                                        :timestamp %
                                        :modified (rand-int 50))
                             (range 50))
          event-batch-2 (map #(hash-map :id (str (java.util.UUID/randomUUID))
                                        :batch 2
                                        :timestamp %
                                        :modified (rand-int 50))
                             (range 50 90))]

      (sut/store-batch event-store event-batch-1)
      (sut/store-batch event-store event-batch-2)
      (es-index/refresh! es-conn indexname)
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
            {fetched-events-3 :data
             search_after-3 :search_after} (sut/fetch-batch event-store
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
            "desc sort was not properly applied"))))

  (testing "query-fetch-batch should properly sort entities on modified field"
    (let [indexname "test_index"
          tool-store {:conn es-conn
                      :indexname indexname
                      :props {:write-index indexname}
                      :mapping "tool"
                      :type "tool"
                      :settings {}
                      :config {}}
          tool-batch (map #(hash-map :id (str "tool-" %)
                                     :timestamp (rand-int 100)
                                     :modified %
                                     :created %)
                          (range 100))
          malware-store {:conn es-conn
                         :indexname indexname
                         :mapping "malware"
                         :props {:write-index indexname}
                         :type "malware"
                         :settings {}
                         :config {}}
          malware-batch (map #(hash-map :id (str "malware-" %)
                                        :timestamp (rand-int 100)
                                        :modified %
                                        :created %)
                             (range 100))]
      (sut/store-batch tool-store tool-batch)
      (sut/store-batch malware-store malware-batch)
      (es-index/refresh! es-conn indexname)
      (let [{fetched-tool-asc :data} (sut/fetch-batch tool-store 80 0 "asc" nil)
            {fetched-tool-desc :data} (sut/fetch-batch tool-store 80 0 "desc" nil)
            {fetched-malware-asc :data} (sut/fetch-batch malware-store 80 0 "asc" nil)
            {fetched-malware-desc :data} (sut/fetch-batch malware-store 80 0 "desc" nil)]
        (is (apply < (map :modified (concat fetched-tool-asc))))
        (is (apply > (map :modified (concat fetched-tool-desc))))
        (is (apply < (map :modified (concat fetched-malware-asc))))
        (is (apply > (map :modified (concat fetched-malware-desc)))))))

  (es-index/delete! es-conn "test_*"))

(deftest fetch-deletes-test
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")
  (post-bulk examples)
  (es-index/refresh! es-conn) ;; ensure indices refresh
  (let [total-examples (->> (map count (vals examples))
                            (apply +))
        [sighting1 sighting2] (:parsed-body (helpers/get "ctia/sighting/search"
                                                         :query-params {:limit 2 :query "*"}
                                                         :headers {"Authorization" "45c1f5e3f05d0"}))
        [tool1 tool2 tool3] (:parsed-body (helpers/get "ctia/tool/search"
                                                 :query-params {:limit 3 :query "*"}
                                                 :headers {"Authorization" "45c1f5e3f05d0"}))
        [malware1] (:parsed-body (helpers/get "ctia/malware/search"
                                              :query-params {:limit 1 :query "*"}
                                              :headers {"Authorization" "45c1f5e3f05d0"}))
        sighting1-id (-> sighting1 :id long-id->id :short-id)
        sighting2-id (-> sighting2 :id long-id->id :short-id)
        tool1-id (-> tool1 :id long-id->id :short-id)
        tool2-id (-> tool2 :id long-id->id :short-id)
        tool3-id (-> tool3 :id long-id->id :short-id)
        malware1-id (-> malware1 :id long-id->id :short-id)
        _ (delete (format "ctia/sighting/%s" sighting1-id)
                  :headers {"Authorization" "45c1f5e3f05d0"})
        _ (es-index/refresh! es-conn)
        since (time/now)
        _ (delete (format "ctia/sighting/%s" sighting2-id)
                  :headers {"Authorization" "45c1f5e3f05d0"})
        _ (delete (format "ctia/tool/%s" tool1-id)
                  :headers {"Authorization" "45c1f5e3f05d0"})
        _ (delete (format "ctia/tool/%s" tool2-id)
                  :headers {"Authorization" "45c1f5e3f05d0"})
        _ (delete (format "ctia/tool/%s" tool3-id)
                  :headers {"Authorization" "45c1f5e3f05d0"})
        _ (delete (format "ctia/tool/%s" malware1-id)
                  :headers {"Authorization" "45c1f5e3f05d0"})
        {data1 :data paging1 :paging} (sut/fetch-deletes [:sighting :tool] since 3 nil)
        {data2 :data paging2 :paging} (sut/fetch-deletes [:sighting :tool]
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
    (is (nil? (:malware data2)) "fetch-deletes shall only retrieve entity types given as parameter")))

(deftest init-get-migration-test
  (post-bulk examples)
  (es-index/refresh! es-conn) ; ensure indices refresh
  (testing "init-migration should properly create new migration state from selected types."
    (let [prefix "0.0.0"
          entity-types [:tool :malware :relationship]
          migration-id-1 "migration-1"
          migration-id-2 "migration-2"
          fake-migration (sut/init-migration migration-id-1
                                             prefix
                                             entity-types
                                             false)
          real-migration-from-init (sut/init-migration migration-id-2
                                                       prefix
                                                       entity-types
                                                       true)
          check-state (fn [{:keys [id created stores]} migration-id message]
                        (testing message
                          (is (= id migration-id))
                          (is (= (set (keys stores))
                                 (set entity-types)))
                          (doseq [entity-type entity-types]
                            (let [{:keys [source target started completed]} (get stores entity-type)
                                  created-indices (es-index/get es-conn (str (:index target) "*"))]
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
      (check-state (sut/get-migration migration-id-2 es-conn)
                   migration-id-2
                   "init-migration shall store confirmed migration, and get-migration should be properly retrieved from store")
      (is (thrown? clojure.lang.ExceptionInfo
                   (sut/get-migration migration-id-1 es-conn))
          "migration-id-1 was not confirmed it should not exist and thus get-migration must raise a proper exception")

      (testing "stored document shall not contains object stores in source and target"
        (let [{:keys [stores]} (es-doc/get-doc es-conn
                                              "ctia_migration"
                                              "migration"
                                              migration-id-2
                                              {})]
          (doseq [store stores]
                  (is (nil? (get-in store [:source :store])))
                  (is (nil? (get-in store [:target :store])))))))))
