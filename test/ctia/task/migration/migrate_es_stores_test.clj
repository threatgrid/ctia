(ns ctia.task.migration.migrate-es-stores-test
  (:require [clojure
             [test :refer [deftest is join-fixtures testing use-fixtures]]
             [walk :refer [keywordize-keys]]]

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
(def minimal-examples (fixt/bundle fixtures-nb false))
(def example-types
  (->> (vals minimal-examples)
       (map #(-> % first :type keyword))
       set))

(defn rollover-post-bulk
  "post data in 2 parts to enable rollover on second post if conditions are met"
  []
  (post-bulk (fixt/bundle (/ fixtures-nb 2) false))
  (post-bulk (fixt/bundle (/ fixtures-nb 2) false)))


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
            (is (= fixtures-nb (:migrated target))))
          )))))


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
      ;;(post-bulk minimal-examples)
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
  (post-bulk minimal-examples)
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
                    (= :event entity-type) (* fixtures-nb (count minimal-examples))
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
                "event - finished migrating 1500 documents"
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
              expected-event-indices {"v0.0.0_ctia_event-000001" 1000
                                      "v0.0.0_ctia_event-000002" 500}
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
                          {(format  "v0.0.0_%s-000001" (:indexname k)) 50
                           (format  "v0.0.0_%s-000002" (:indexname k)) 50
                           (format  "v0.0.0_%s-000003" (:indexname k)) 0}))
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
            (let [search-url (format "http://%s:%s/%s/_search"
                                     (:host default)
                                     (:port default)
                                     (name index))
                  {:keys [body]
                   :as search-res}
                  (client/get search-url {:as :json})
                  hits (->> body
                            :hits
                            :hits
                            (map #(get-in % [:_source :groups])))]
              (is (every? #(= ["migration-test"] %)
                          hits))))))
    (testing "restart migration shall properly handle inserts, updates and deletes"
      (let [new-malwares (->> (fixt/n-examples :malware 3 false)
                              (map #(assoc % :description "INSERTED"))
                              (hash-map :malwares))
            [sighting1 & sightings] (:parsed-body (helpers/get "ctia/sighting/search"
                                                  :query-params {:limit 10 :query "*"}
                                                  :headers {"Authorization" "45c1f5e3f05d0"}))
            sighting1-id (-> sighting1 :id long-id->id :short-id)
            sighting-ids (map #(-> % :id long-id->id :short-id)
                               sightings)]
        (post-bulk new-malwares)
        (put (format "ctia/sighting/%s" sighting1-id)
             :body (-> (dissoc sighting1 :id)
                       (assoc :description "UPDATED"))
             :headers {"Authorization" "45c1f5e3f05d0"})
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
              updated-sighting (-> (es-doc/query conn
                                                 indexname
                                                 mapping
                                                 (es-query/ids [sighting1-id])
                                                 {})
                                   :data
                                   first)
              get-deleted-sightings (-> (es-doc/query conn
                                                      indexname
                                                      mapping
                                                      (es-query/ids sighting-ids)
                                                      {})
                                        :data)]
          (is (= (repeat 3 "INSERTED") (map :description last-target-malwares))
              "inserted malwares must be found in target malware store")

          (is (= "UPDATED" (:description updated-sighting))
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
