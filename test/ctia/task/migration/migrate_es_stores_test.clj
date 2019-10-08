(ns ctia.task.migration.migrate-es-stores-test
  (:require [clojure
             [test :refer [deftest is join-fixtures testing use-fixtures]]
             [walk :refer [keywordize-keys]]]
            [clojure.core.async :as async :refer [chan <!! timeout]]
            [clj-http.fake :refer [with-fake-routes]]
            [schema.core :as s]
            [clj-http.client :as client]
            [clj-momo.test-helpers.core :as mth]
            [clj-momo.lib.es
             [query :as es-query]
             [conn :refer [connect]]
             [document :as es-doc]
             [index :as es-index]]
            [ctim.domain.id :refer [long-id->id]]

            [ctia.properties :as props]
            [ctia.task.rollover :refer [rollover-stores]]
            [ctia.task.migration
             [migrate-es-stores :as sut]
             [store :refer [setup! prefixed-index get-migration fetch-batch]]]
            [ctia.test-helpers
             [fixtures :as fixt]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post put delete post-bulk with-atom-logger]]
             [es :as es-helpers]
             [fake-whoami-service :as whoami-helpers]]
            [ctia.stores.es.store :refer [store->map]]
            [ctia.store :refer [stores]]))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  whoami-helpers/fixture-server
                  whoami-helpers/fixture-reset-state
                  helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store]))

(setup!) ;; init migration conn and properties
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

(defn make-cat-indices-url [host port]
  (format "http://%s:%s/_cat/indices?format=json&pretty=true" host port))

(defn get-cat-indices [host port]
  (let [url (make-cat-indices-url host
                                  port)
        {:keys [body]
         :as cat-response} (client/get url {:as :json})]
    (->> body
         (map (fn [{:keys [index]
                    :as entry}]
                {index (read-string
                        (:docs.count entry))}))
         (into {})
         keywordize-keys)))

(defn index-exists?
  [store prefix]
  (let [{:keys [conn indexname]} (store->map store {})]
    (es-index/index-exists? conn
                            (prefixed-index indexname prefix))))

(def fixtures-nb 100)
(def updates-nb 50)
(def minimal-examples (fixt/bundle fixtures-nb false))
(def example-types
  (->> (vals minimal-examples)
       (map #(-> % first :type keyword))
       set))

(defn update-entity
  [{entity-type :type
    entity-id :short-id}]
  (let [entity-path (format "ctia/%s/%s" (str entity-type) entity-id)
        previous (-> (helpers/get entity-path
                                  :headers {"Authorization" "45c1f5e3f05d0"})
                     :parsed-body)]
  (put entity-path
       :body (assoc previous :description "UPDATED")
       :headers {"Authorization" "45c1f5e3f05d0"})))

(defn random-updates
  "select nb random entities of the bulk and update them"
  [bulk-result nb]
  (let [random-ids (->> (select-keys bulk-result
                                     [:malwares
                                      :sightings
                                      :indicators
                                      :vulnerabilities])
                        vals
                        (apply concat)
                        shuffle
                        (take nb)
                        (map long-id->id))]
    (doseq [entity random-ids]
      (update-entity entity))))

(defn rollover-post-bulk
  "post data in 2 parts with rollover, randomly update son entities"
  []
  (let [bulk-res-1 (post-bulk (fixt/bundle (/ fixtures-nb 2) false))
        _ (rollover-stores @stores)
        bulk-res-2 (post-bulk (fixt/bundle (/ fixtures-nb 2) false))
        _ (rollover-stores @stores)]
    (random-updates bulk-res-1 (/ updates-nb 2))
    (random-updates bulk-res-2 (/ updates-nb 2))))


(deftest migration-with-rollover
  (helpers/set-capabilities! "foouser"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")
  (testing "migration with rollover and multiple indices for source stores"
    (let [store-types [:malware :tool :incident]]
      (rollover-post-bulk)
      ;; insert malformed documents
      (doseq [store-type store-types]
        (es-index/get es-conn
                      (str (get-in es-props [store-type :indexname]) "*")))
      (sut/migrate-store-indexes "test-3"
                                 "0.0.0"
                                 [:__test]
                                 store-types
                                 10
                                 30
                                 true
                                 false)

      (let [migration-state (es-doc/get-doc es-conn
                                            migration-index
                                            "migration"
                                            "test-3"
                                            {})]
        (doseq [store-type store-types]
          (is (= (count (es-index/get es-conn
                                      (str "v0.0.0_" (get-in es-props [store-type :indexname]) "*")))
                 3)
              "target indice should be rolledover during migration")
          (es-index/get es-conn
                        (str "v0.0.0_" (get-in es-props [store-type :indexname]) "*"))
          (let [migrated-store (get-in migration-state [:stores store-type])
                {:keys [source target]} migrated-store]
            (is (= fixtures-nb (:total source)))
            (is (= fixtures-nb (:migrated target)))))))))

(deftest sliced-migration-test
  (with-open [rdr (clojure.java.io/reader"./test/data/indices/sample-relationships-1000.json")]
    (let [storemap {:conn es-conn
                    :indexname "ctia_relationship"
                    :mapping "relationship"
                    :props {:write-index "ctia_relationship"}
                    :type "relationship"
                    :settings {}
                    :config {}}
          {wo-modified true
           w-modified false} (->> (line-seq rdr)
                                  (map es-helpers/prepare-bulk-ops)
                                  (group-by #(nil? (:modified %))))
          sorted-w-modified (sort-by :modified w-modified)
          bulk-1 (concat wo-modified (take 500 sorted-w-modified))
          bulk-2 (drop 500 sorted-w-modified)
          logger-1 (atom [])
          _ (es-helpers/load-bulk es-conn bulk-1)
          _ (with-atom-logger logger-1
              (sut/migrate-store-indexes "migration-test-4"
                                         "0.0.0"
                                         [:__test]
                                         [:relationship]
                                         100
                                         30
                                         true
                                         false))
          migration-state-1 (es-doc/get-doc es-conn
                                            migration-index
                                            "migration"
                                            "migration-test-4"
                                            {})
          target-count-1 (es-doc/count-docs es-conn
                                            "v0.0.0_ctia_relationship"
                                            "relationship"
                                            nil)
          _ (es-helpers/load-bulk es-conn bulk-2)
          logger-2 (atom [])
          _ (with-atom-logger logger-1
              (sut/migrate-store-indexes "migration-test-4"
                                         "0.0.0"
                                         [:__test]
                                         [:relationship]
                                         100
                                         30
                                         true
                                         true))
          target-count-2 (es-doc/count-docs es-conn
                                            "v0.0.0_ctia_relationship"
                                            "relationship"
                                            nil)
          migration-state-2 (es-doc/get-doc es-conn
                                            migration-index
                                            "migration"
                                            "migration-test-4"
                                            {})]
      (is (= (+ 500 (count wo-modified))
             target-count-1
             (get-in migration-state-1 [:stores :relationship :target :migrated])
             (get-in migration-state-1 [:stores :relationship :source :total]))
          "migration process should start with documents missing field used for bucketizing")

      (is (= 1000
             target-count-2
             (get-in migration-state-2 [:stores :relationship :source :total]))
          "migration process should complete the migration after restart"))))

(deftest migration-slow-es-read
  (testing "migration with rollover and multiple indices for source stores"
    (let [store-types [:malware :tool :incident]
          migration-id "test-slow-read"
          nb-slow-reqs (atom 5)]
      (rollover-post-bulk)
      ;; insert malformed documents
      (doseq [store-type store-types]
        (es-index/get es-conn
                      (str (get-in es-props [store-type :indexname]) "*")))
      (with-redefs [es-doc/search-docs
                    (fn [es-conn index-name mapping es-query all-of params]
                      (when (< 0 @nb-slow-reqs)
                        (println "TEST " @nb-slow-reqs)
                        (<!! (timeout (/ 5000 @nb-slow-reqs)))
                        (swap! nb-slow-reqs dec))
                      (es-doc/query es-conn index-name mapping {:match_all {}} params))]
          (sut/migrate-store-indexes migration-id
                                     "0.0.0"
                                     [:__test]
                                     store-types
                                     10
                                     30
                                     true
                                     false))

      (let [migration-state (es-doc/get-doc es-conn
                                            migration-index
                                            "migration"
                                            migration-id
                                            {})]
        (doseq [store-type store-types]
          (is (= (count (es-index/get es-conn
                                      (str "v0.0.0_" (get-in es-props [store-type :indexname]) "*")))
                 3)
              "target indice should be rolledover during migration")
          (es-index/get es-conn
                        (str "v0.0.0_" (get-in es-props [store-type :indexname]) "*"))
          (let [migrated-store (get-in migration-state [:stores store-type])
                {:keys [source target]} migrated-store]
            (is (= fixtures-nb (:total source)))
            (is (= fixtures-nb (:migrated target)))))))))


(deftest migration-with-malformed-docs
  (helpers/set-capabilities! "foouser"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")
  (testing "migration with malformed documents in store"
    (let [store-types [:malware :tool :incident]
          logger (atom [])
          bad-doc {:id 1
                   :hey "I"
                   :am "a"
                   :bad "document"}]
      ;; insert proper documents
      (rollover-post-bulk)
      ;; insert malformed documents
      (doseq [store-type store-types]
        (es-doc/create-doc es-conn
                           (str (get-in es-props [store-type :indexname]) "-write")
                           (name store-type)
                           bad-doc
                           "true"))
      (with-atom-logger logger
        (sut/migrate-store-indexes "test-3"
                                   "0.0.0"
                                   [:__test]
                                   store-types
                                   10
                                   30
                                   true
                                   false))
      (let [messages (set @logger)
            migration-state (es-doc/get-doc es-conn
                                            migration-index
                                            "migration"
                                            "test-3"
                                            {})]
        (doseq [store-type store-types]
          (let [migrated-store (get-in migration-state [:stores store-type])
                {:keys [source target]} migrated-store]
            (is (= (inc fixtures-nb) (:total source)))
            (is (= fixtures-nb (:migrated target))))
          (is (some #(clojure.string/starts-with? % (format "%s - Cannot migrate entity: {"
                                                            (name store-type)))
                    messages)
              (format "malformed %s was not logged" store-type)))))))


(deftest test-migrate-store-indexes
  ;; TODO clean data
  (helpers/set-capabilities! "foouser"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")
  ;; insert proper documents
  (rollover-post-bulk)
  (testing "migrate ES Stores test setup"
    (testing "simulate migrate es indexes shall not create any document"
      (sut/migrate-store-indexes "test-1"
                                 "0.0.0"
                                 [:0.4.16]
                                 (keys @stores)
                                 10
                                 30
                                 false
                                 false)

      (doseq [store (vals @stores)]
        (is (not (index-exists? store "0.0.0"))))
      (is (nil? (seq (es-doc/get-doc es-conn
                                     (get-in es-props [:migration :indexname])
                                     "migration"
                                     "test-1"
                                     {}))))))
  (testing "migrate es indexes"
    (let [logger (atom [])]
      (with-atom-logger logger
        (sut/migrate-store-indexes "test-2"
                                   "0.0.0"
                                   [:__test]
                                   (keys @stores)
                                   10
                                   30
                                   true
                                   false))
      (testing "shall generate a proper migration state"
        (let [migration-state (es-doc/get-doc es-conn
                                              migration-index
                                              "migration"
                                              "test-2"
                                              {})]
          (is (= (set (keys @stores))
                 (set (keys (:stores migration-state)))))
          (doseq [[entity-type migrated-store] (:stores migration-state)]
            (let [{:keys [source target started completed]} migrated-store
                  source-size
                  (cond
                    (= :identity entity-type) 1
                    (= :event entity-type) (+ updates-nb
                                              (* fixtures-nb
                                                 (count minimal-examples)))
                    (contains? example-types (keyword entity-type)) fixtures-nb
                    :else 0)]
              (is (= source-size (:total source)))
              (is (not (nil? started)))
              (is (not (nil? completed)))
              (is (>= (:total source) (:migrated target)))
              (is (int? (:total source)))
              (is (= (:index target)
                     (prefixed-index (:index source) "0.0.0")))))))
      (testing "shall produce valid logs"
        (let [messages (set @logger)]
          (is (contains? messages "set batch size: 10"))
          (is (clojure.set/subset?
               ["campaign - finished migrating 100 documents"
                "indicator - finished migrating 100 documents"
                (format "event - finished migrating %s documents"
                        (+ 1500 updates-nb))
                "actor - finished migrating 100 documents"
                "relationship - finished migrating 100 documents"
                "incident - finished migrating 100 documents"
                "investigation - finished migrating 100 documents"
                "coa - finished migrating 100 documents"
                "identity - finished migrating 0 documents"
                "judgement - finished migrating 100 documents"
                "data-table - finished migrating 0 documents"
                "feedback - finished migrating 0 documents"
                "casebook - finished migrating 100 documents"
                "sighting - finished migrating 100 documents"
                "attack-pattern - finished migrating 100 documents"
                "malware - finished migrating 100 documents"
                "tool - finished migrating 100 documents"
                "vulnerability - finished migrating 100 documents"
                "weakness - finished migrating 100 documents"]
               messages))))

      (testing "shall produce new indices
                  with enough documents and the right transforms"
        (let [{:keys [default
                      data-table
                      relationship
                      judgement
                      investigation
                      coa
                      tool
                      attack-pattern
                      malware
                      incident
                      event
                      indicator
                      campaign
                      sighting
                      casebook
                      actor
                      vulnerability
                      weakness]
               :as es-props}
              (get-in @props/properties [:ctia :store :es])
              date (java.util.Date.)
              index-date (.format (java.text.SimpleDateFormat. "yyyy.MM.dd") date)
              expected-event-indices {(format "v0.0.0_ctia_event-%s-000001" index-date)
                                      1000
                                      (format "v0.0.0_ctia_event-%s-000002" index-date)
                                      (+ 500 updates-nb)}
              expected-indices
              (->> #{relationship
                     judgement
                     coa
                     attack-pattern
                     malware
                     tool
                     incident
                     indicator
                     investigation
                     campaign
                     casebook
                     sighting
                     actor
                     vulnerability
                     weakness}
                   (map (fn [k]
                          {(format  "v0.0.0_%s-%s-000001" (:indexname k) index-date) 50
                           (format  "v0.0.0_%s-%s-000002" (:indexname k) index-date) 50
                           (format  "v0.0.0_%s-%s-000003" (:indexname k) index-date) 0}))
                   (into expected-event-indices)
                   keywordize-keys)
              _ (es-index/refresh! es-conn)
              formatted-cat-indices (get-cat-indices (:host default)
                                                     (:port default))]
          (is (= expected-indices
                 (select-keys formatted-cat-indices
                              (keys expected-indices))))

          (doseq [[index _]
                  expected-indices]
            (let [docs (->> (es-doc/search-docs es-conn (name index) nil nil nil {})
                            :data
                            (map :groups))]
              (is (every? #(= ["migration-test"] %)
                          docs))))))
    (testing "restart migration shall properly handle inserts, updates and deletes"
      (let [;; retrieve the first 2 source indices for sighting store
            {:keys [host port]}(get-in @props/properties [:ctia :store :es :default])
            [sighting-index-1 sighting-index-2]
            (->> (get-cat-indices host port)
                 keys
                 (map name)
                 (filter #(.contains % "sighting"))
                 sort
                 (take 2))

            ;; retrieve source entity to update, in first position of first index
            es-sighting0 (-> (es-doc/query es-conn
                                           sighting-index-1
                                           "sighting"
                                           {:match_all {}}
                                           {:sort_by "timestamp:asc"
                                            :size 1})
                             :data
                             first)
            ;; retrieve source entity to update, in first position of second index
            es-sighting1 (-> (es-doc/query es-conn
                                           sighting-index-2
                                           "sighting"
                                           {:match_all {}}
                                           {:sort_by "timestamp:asc"
                                            :size 1})
                             :data
                             first)

            ;; prepare new malwares
            new-malwares (->> (fixt/n-examples :malware 3 false)
                              (map #(assoc % :description "INSERTED"))
                              (hash-map :malwares))

            ;; retrieve 5 source entities to delete, in last positions of first index
            es-sightings-1 (-> (es-doc/query es-conn
                                             sighting-index-1
                                             "sighting"
                                             {:match_all {}}
                                             {:sort_by "timestamp:desc"
                                              :limit 5})
                               :data)
            ;; retrieve 5 source entities to delete, in last positions of second index
            es-sightings-2 (-> (es-doc/query es-conn
                                             sighting-index-2
                                             "sighting"
                                             {:match_all {}}
                                             {:sort_by "timestamp:desc"
                                              :limit 5})
                               :data)
            sightings (concat es-sightings-1 es-sightings-2)
            sighting0-id (:id es-sighting0)
            sighting1-id (:id es-sighting1)
            sighting-ids (map :id sightings)
            updated-sighting-body (-> (:sightings minimal-examples)
                                      first
                                      (dissoc :id)
                                      (assoc :description "UPDATED"))]
        ;; insert new entities in source store
        (post-bulk new-malwares)
        ;; modify entities in first and second source indices
        (put (format "ctia/sighting/%s" sighting0-id)
             :body updated-sighting-body
             :headers {"Authorization" "45c1f5e3f05d0"})
        (put (format "ctia/sighting/%s" sighting1-id)
             :body updated-sighting-body
             :headers {"Authorization" "45c1f5e3f05d0"})
        ;; delete entities from first and second source indices
        (doseq [sighting-id sighting-ids]
          (delete (format "ctia/sighting/%s" sighting-id)
                  :headers {"Authorization" "45c1f5e3f05d0"}))
        (sut/migrate-store-indexes "test-2"
                                   "0.0.0"
                                   [:__test]
                                   (keys @stores)
                                   2 ;; small batch to check proper delete paging
                                   10
                                   true
                                   true)
        (let [migration-state (get-migration "test-2" es-conn)
              malware-migration (get-in migration-state [:stores :malware])
              sighting-migration (get-in migration-state [:stores :sighting])
              malware-target-store (get-in malware-migration [:target :store])
              {last-target-malwares :data} (fetch-batch malware-target-store 3 0 "desc" nil)
              {:keys [conn indexname mapping]} (get-in sighting-migration [:target :store])
              updated-sightings (-> (es-doc/query conn
                                                  indexname
                                                  mapping
                                                  (es-query/ids [sighting0-id sighting1-id])
                                                  {})
                                    :data)
              get-deleted-sightings (-> (es-doc/query conn
                                                      indexname
                                                      mapping
                                                      (es-query/ids sighting-ids)
                                                      {})
                                        :data)]
          (is (= (repeat 3 "INSERTED") (map :description last-target-malwares))
              "inserted malwares must be found in target malware store")

          (is (= (repeat 2 "UPDATED") (map :description updated-sightings))
              "updated document must be updated in target stores")
          (is (empty? get-deleted-sightings)
              "deleted document must not be in target stores")))))))

(defn load-test-fn
  [maximal?]
  ;; insert 20000 docs per entity-type
  (doseq [bundle (repeatedly 20 #(fixt/bundle 1000 maximal?))]
    (post-bulk bundle))
  (doseq [batch-size [1000 3000 6000 10000]]
    (let [total-docs (* (count example-types) 20000)
          _ (println (format "===== migrating %s documents with batch size %s"
                             total-docs
                             batch-size))
          migration-id (format "test-load-%s" batch-size)
          prefix (format "test_load_%s" batch-size)
          start (System/currentTimeMillis)
          _ (sut/migrate-store-indexes migration-id
                                       prefix
                                       [:__test]
                                       (into [] example-types)
                                       batch-size
                                       30
                                       true
                                       false)
          end (System/currentTimeMillis)
          total (/ (- end start) 1000)
          doc-per-sec (/ total-docs total)
          migration-state (es-doc/get-doc es-conn
                                          migration-index
                                          "migration"
                                          migration-id
                                          {})]
      (println "total: " (float total))
      (println "documents per seconds: " (float doc-per-sec))
      (doseq [[k state] (:stores migration-state)]
        (is (= 20000
               (get-in state [:source :total])
               (get-in state [:target :migrated]))))
      (es-index/delete! es-conn (format "v%s*" prefix))
      (es-doc/delete-doc es-conn migration-index "migration" migration-id "true")))
  (es-index/delete! es-conn "ctia_*"))

;;(deftest ^:integration minimal-load-test
;;  (testing "load testing with minimal entities"
;;    (println "load testing with minimal entities")
;;    (load-test-fn false)))

;;(deftest ^:integration maximal-load-test
;;  (testing "load testing with maximal entities"
;;    (println "load testing with maximal entities")
;;    (load-test-fn true)))
