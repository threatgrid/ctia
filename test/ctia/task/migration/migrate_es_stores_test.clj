(ns ctia.task.migration.migrate-es-stores-test
  (:require [clj-momo.lib.clj-time.coerce :as time-coerce]
            [clj-momo.lib.es.document :as es-doc]
            [clj-momo.test-helpers.core :as mth]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [clojure.walk :refer [keywordize-keys]]
            [ctia.entity.relationship.schemas :refer [StoredRelationship]]
            [ctia.stores.es.store :refer [store->map]]
            [ctia.task.migration.migrate-es-stores :as sut]
            [ctia.task.migration.store
             :refer
             [fetch-batch get-migration init-migration prefixed-index setup!]]
            [ctia.task.rollover :refer [rollover-stores]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core
             :as
             helpers
             :refer
             [GET DELETE POST-bulk PUT with-atom-logger]]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.fixtures :as fixt]
            [ctia.test-helpers.migration :refer [app->MigrationStoreServices]]
            [ctim.domain.id :refer [long-id->id]]
            [ductile.conn :refer [connect]]
            [ductile.document :as ductile.doc]
            [ductile.index :as es-index]
            [ductile.query :as es-query]
            [schema.core :as s])
  (:import clojure.lang.ExceptionInfo
           java.lang.AssertionError
           java.text.SimpleDateFormat
           java.util.Date))

(defn fixture-setup! [f]
  (let [app (helpers/get-current-app)
        services (app->MigrationStoreServices app)]
    (setup! services)
    (f)))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  whoami-helpers/fixture-server
                  es-helpers/fixture-properties:es-store]))

(defn es-props [get-in-config]
  (get-in-config [:ctia :store :es]))

(defn es-conn [get-in-config]
  (connect (:default (es-props get-in-config))))

(defn migration-index [get-in-config]
  (get-in-config [:ctia :migration :store :es :migration :indexname]))

(defn fixture-clean-migration [t]
  (let [app (helpers/get-current-app)
        {:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
    (try
      (t)
      (finally
        (doto (es-conn get-in-config)
          (es-index/delete! "v0.0.0*")
          (es-index/delete! (str (migration-index get-in-config) "*")))))))

(s/defn with-each-fixtures*
  "Wrap this function around each deftest instead of use-fixtures
  so the TK config can be transformed before helpers/fixture-ctia
  starts the app."
  [config-transformer body-fn :- (s/=> s/Any (s/=> s/Any (s/named s/Any 'app)))]
  (let [fixtures (join-fixtures [helpers/fixture-ctia
                                 fixture-setup! ;; Note: goes _after_ fixture-ctia
                                 es-helpers/fixture-delete-store-indexes
                                 fixture-clean-migration])]
    (helpers/with-config-transformer*
      config-transformer
      #(fixtures
         (fn []
           (body-fn (helpers/get-current-app)))))))

(defmacro with-each-fixtures
  "Primarily for check-migration-params-test. Binds `app` to current TK app."
  [config-transformer app & body]
  (assert (simple-symbol? app) (pr-str app))
  `(with-each-fixtures* ~config-transformer
     (fn [~app]
       (do ~@body))))

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
  [app
   {entity-type :type
    entity-id :short-id}]
  (let [entity-path (format "ctia/%s/%s" (str entity-type) entity-id)
        previous (-> (GET app
                          entity-path
                          :headers {"Authorization" "45c1f5e3f05d0"})
                     :parsed-body)]
    (PUT app
         entity-path
         :body (assoc previous :description "UPDATED")
         :headers {"Authorization" "45c1f5e3f05d0"})))

(defn random-updates
  "select nb random entities of the bulk and update them"
  [app bulk-result nb]
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
      (update-entity app entity))))

(defn rollover-post-bulk
  "post data in 2 parts with rollover, randomly update son entities"
  [app all-stores]
  (let [bulk-res-1 (POST-bulk app (fixt/bundle (/ fixtures-nb 2) false))
        _ (rollover-stores (all-stores))
        bulk-res-2 (POST-bulk app (fixt/bundle (/ fixtures-nb 2) false))
        _ (rollover-stores (all-stores))]
    (random-updates app bulk-res-1 (/ updates-nb 2))
    (random-updates app bulk-res-2 (/ updates-nb 2))))

(deftest check-migration-params-test
  (let [migration-params {:migration-id "id"
                          :prefix       "1.2.0"
                          :migrations   [:identity]
                          :store-keys   [:incident :investigation :malware]
                          :batch-size   100
                          :buffer-size  3
                          :confirm? true
                          :restart? false}]
    (testing "misconfigured migration"
      (with-each-fixtures #(-> %
                               (assoc-in [:ctia :store :es :investigation :indexname]
                                         "v1.2.0_ctia_investigation")
                               (assoc-in [:malware 0 :state :props :indexname]
                                         "v1.2.0_ctia_malware"))
        app
        (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
          (let [v (get-in-config [:ctia :store :es :investigation :indexname])]
            (assert (= v "v1.2.0_ctia_investigation") v))
          (let [v (get-in-config [:malware 0 :state :props :indexname])]
            (assert (= v "v1.2.0_ctia_malware") v))
          (is (thrown? AssertionError
                       (sut/check-migration-params migration-params
                                                   get-in-config))
              "source and target store must be different")))
      (with-each-fixtures identity app
        (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
          (is (thrown? ExceptionInfo
                       (sut/check-migration-params (update migration-params
                                                           :migrations
                                                           conj
                                                           :bad-migration-id)
                                                   get-in-config))))))
    (testing "properly configured migration"
      (with-each-fixtures identity app
        (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
          (is (sut/check-migration-params migration-params
                                          get-in-config)))))))

(deftest prepare-params-test
  (let [migration-props {:buffer-size 3,
                         :batch-size 100,
                         :migration-id "migration-test",
                         :restart? false,
                         :store-keys "malware,  tool,sighting  ",
                         :migrations "identity",
                         :confirm? false,
                         :prefix "v1.2.0"}
        prepared (sut/prepare-params migration-props)]
    (testing "prepare params should properly format migration properties"
      (is (= (dissoc prepared :store-keys :migrations)
             (dissoc migration-props :store-keys :migrations)))
      (is (= (:store-keys prepared)
             [:malware :tool :sighting]))
      (is (= (:migrations prepared)
             [:identity])))))

(deftest migration-with-rollover
 (with-each-fixtures identity app
  (testing "migration with rollover and multiple indices for source stores"
    (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
          {:keys [all-stores]} (helpers/get-service-map app :StoreService)
          services (app->MigrationStoreServices app)

          store-types [:malware :tool :incident]]
      (helpers/set-capabilities! app
                                 "foouser"
                                 ["foogroup"]
                                 "user"
                                 all-capabilities)
      (whoami-helpers/set-whoami-response app
                                          "45c1f5e3f05d0"
                                          "foouser"
                                          "foogroup"
                                          "user")
      (rollover-post-bulk app all-stores)
      ;; insert malformed documents
      (doseq [store-type store-types]
        (es-index/get (es-conn get-in-config)
                      (str (get-in (es-props get-in-config) [store-type :indexname]) "*")))
      (sut/migrate-store-indexes {:migration-id "test-3"
                                  :prefix       "0.0.0"
                                  :migrations   [:__test]
                                  :store-keys   store-types
                                  :batch-size   10
                                  :buffer-size  3
                                  :confirm?     true
                                  :restart?     false}
                                 services)
      (let [migration-state (es-doc/get-doc (es-conn get-in-config)
                                            (migration-index get-in-config)
                                            "migration"
                                            "test-3"
                                            {})]
          (doseq [store-type store-types]
            (is (= (count (es-index/get (es-conn get-in-config)
                                        (str "v0.0.0_" (get-in (es-props get-in-config) [store-type :indexname]) "*")))
                   3)
                "target indice should be rolledover during migration")
            (es-index/get (es-conn get-in-config)
                          (str "v0.0.0_" (get-in (es-props get-in-config) [store-type :indexname]) "*"))
            (let [migrated-store (get-in migration-state [:stores store-type])
                  {:keys [source target]} migrated-store]
              (is (= fixtures-nb (:total source)))
              (is (= fixtures-nb (:migrated target))))))))))

(def date-str->epoch-millis
  (comp time-coerce/to-long time-coerce/to-date-time))

(deftest read-source-batch-test
  (with-each-fixtures identity app
    (with-open [rdr (io/reader "./test/data/indices/sample-relationships-1000.json")]
      (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)

            storemap {:conn (es-conn get-in-config)
                      :indexname "ctia_relationship"
                      :mapping "relationship"
                      :props {:write-index "ctia_relationship"}
                      :type "relationship"
                      :settings {}
                      :config {}}
            docs (map es-helpers/prepare-bulk-ops
                      (line-seq rdr))
            _ (es-helpers/load-bulk (es-conn get-in-config) docs)
            no-meta-docs (map #(dissoc % :_index :_type :_id)
                              docs)
            docs-no-modified (filter #(nil? (:modified %))
                                     no-meta-docs)
            docs-100 (take 100 no-meta-docs)
            missing-query {:bool {:must_not {:exists {:field :modified}}}}
            ids-100-query {:ids {:values (map :id docs-100)}}
            match-all-query {:match_all {}}
            nb-skipped-ids 10
            [last-skipped & expected-ids-docs] (->> (sort-by (juxt :modified :created :id)
                                                             docs-100)
                                                    (drop (dec nb-skipped-ids)))
            {after-modified :modified
             after-created :created
             after-id :id} last-skipped
            search_after [(date-str->epoch-millis after-modified)
                          (date-str->epoch-millis after-created)
                          after-id]
            read-params-1 {:source-store storemap
                           :batch-size 1000
                           :query missing-query}
            read-params-2 {:source-store storemap
                           :batch-size 100
                           :search_after search_after
                           :query ids-100-query}
            read-params-3 {:source-store storemap
                           :batch-size 400
                           :query match-all-query}
            missing-res (sut/read-source-batch read-params-1)
            ids-res (sut/read-source-batch read-params-2)
            match-all-res (rest (iterate sut/read-source-batch read-params-3))]

        (testing "queries should be used to select data"
          (is (= (set docs-no-modified)
                 (set (:documents missing-res)))))
        (testing "search_after should be properly taken into account"
          (is (= (set expected-ids-docs)
                 (set (:documents ids-res))))
          (is (= (- 100 nb-skipped-ids)
                 (count (:documents ids-res)))))
        (testing "read source should return parameters for next call"
          (is (= read-params-1
                 (dissoc missing-res :search_after :documents)))
          (is (= (dissoc read-params-2 :search_after)
                 (dissoc ids-res :search_after :documents))))
        (testing "read-source-batch shoould return nil when parameters map is nil or the query result is empty"
          (is (= nil
                 (sut/read-source-batch nil)
                 (sut/read-source-batch (assoc read-params-1
                                               :batch-size
                                               0)))))
        (testing "read-source-batch result should be usable to call read-source-batch again for scrolling given query"
          (is (= '(400 400 200 0)
                 (->> (take 4 match-all-res)
                      (map #(-> % :documents count)))))
          (is (= (set no-meta-docs)
                 (->> (take 4 match-all-res)
                      (map :documents)
                      (apply concat)
                      set))))))))

(deftest read-source-test
  (with-each-fixtures identity app
    (testing "read-source should produce a lazy seq from recursive read-source-batch calls"
      (let [counter (atom 0)]
        (with-redefs [sut/read-source-batch (fn [batch-params]
                                              (when (< @counter 5)
                                                (swap! counter inc)
                                                (update batch-params :migrated-count inc)))]
          (let [batches (map :migrated-count
                             (sut/read-source {:migrated-count 0}))]
            (is (= '(1 2) (take 2 batches)))
            (is (= 2 @counter))
            (is (= '(1 2 3 4 5) (take 10 batches)))
            (is (= 5 @counter))))))))

(deftest write-target-test
  (with-each-fixtures identity app
    (with-open [rdr (io/reader "./test/data/indices/sample-relationships-1000.json")]
      (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
            services (app->MigrationStoreServices app)

            prefix "0.0.1"
            indexname "v0.0.1_ctia_relationship"
            storemap {:conn (es-conn get-in-config)
                      :indexname indexname
                      :mapping "relationship"
                      :props {:write-index indexname}
                      :type "relationship"
                      :settings {}
                      :config {}}
            list-coerce (sut/list-coerce-fn StoredRelationship)
            migration-id "migration-1"
            docs (map (comp :_source es-helpers/str->doc)
                      (line-seq rdr))
            base-params {:target-store storemap
                         :entity-type :relationship
                         :list-coerce list-coerce
                         :migration-id migration-id
                         :migrations (sut/compose-migrations [:__test])
                         :batch-size 1000
                         :migration-es-conn (es-conn get-in-config)
                         :confirm? true}
            test-fn (fn [total
                         migrated-count
                         msg
                         {:keys [confirm?]
                          :as override-params}]
                      (init-migration {:migration-id migration-id
                                       :prefix prefix
                                       :store-keys [:relationship]
                                       :confirm? true
                                       :migrations [:__test]
                                       :batch-size 1000
                                       :buffer-size 3
                                       :restart? false}
                                      services)
                      (let [test-docs (take total docs)
                            search_after [(rand-int total)]
                            batch-params  (-> (into base-params
                                                    override-params)
                                              (assoc :documents test-docs
                                                     :search_after search_after))
                            nb-migrated (sut/write-target migrated-count
                                                          batch-params
                                                          services)
                            {target-state :target
                             source-state :source} (-> (get-migration migration-id
                                                                      (es-conn get-in-config)
                                                                      services)
                                                       :stores
                                                       :relationship)
                            _ (es-index/refresh! (es-conn get-in-config))
                            migrated-docs (:data (ductile.doc/query (es-conn get-in-config)
                                                                    indexname
                                                                    {:match_all {}}
                                                                    {:limit total}))]
                        (testing msg
                          (when-not confirm?
                            (is (= (+ total migrated-count)
                                   nb-migrated))
                            (is (= (count migrated-docs)
                                   (:migrated target-state))))
                          (when confirm?
                            (is (= (+ total migrated-count)
                                   (+ (count migrated-docs) migrated-count)
                                   nb-migrated
                                   (:migrated target-state)))
                            (is (= (set (map #(dissoc % :groups)
                                             migrated-docs))
                                   (set (map #(dissoc % :groups)
                                             test-docs)))
                                "write-target should only perform attended modifications")
                            (is (every? #(= (:groups %)
                                            ["migration-test"])
                                        migrated-docs)
                                "write-target should perform attended modifications on migrated documents")
                            (is (= search_after
                                   (:search_after source-state))
                                "write-target should store last search_after in migration state")))))]
        (test-fn 100
                 0
                 "write-target should properly modify documents, and write them in target index"
                 {:confirm? true})

        (test-fn 100
                 10
                 "write-target should properly accumulate migration count"
                 {:confirm? true})

        (test-fn 100
                 0
                 "write-target should not write anything while properly simulating migration when `confirm?` is set to false"
                 {:confirm? false})))))

(deftest sliced-migration-test
  (with-each-fixtures identity app
    (with-open [rdr (io/reader "./test/data/indices/sample-relationships-1000.json")]
      (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
            services (app->MigrationStoreServices app)

            {wo-modified true
             w-modified false} (->> (line-seq rdr)
                                    (map es-helpers/prepare-bulk-ops)
                                    (group-by #(nil? (:modified %))))
            sorted-w-modified (sort-by :modified w-modified)
            bulk-1 (concat wo-modified (take 500 sorted-w-modified))
            bulk-2 (drop 500 sorted-w-modified)
            logger-1 (atom [])
            _ (es-helpers/load-bulk (es-conn get-in-config) bulk-1)
            _ (with-atom-logger logger-1
                (sut/migrate-store-indexes {:migration-id "migration-test-4"
                                            :prefix       "0.0.0"
                                            :migrations   [:__test]
                                            :store-keys   [:relationship]
                                            :batch-size   100
                                            :buffer-size  3
                                            :confirm?     true
                                            :restart?     false}
                                           services))
            migration-state-1 (es-doc/get-doc (es-conn get-in-config)
                                              (migration-index get-in-config)
                                              "migration"
                                              "migration-test-4"
                                              {})
            target-count-1 (ductile.doc/count-docs (es-conn get-in-config)
                                                   "v0.0.0_ctia_relationship"
                                                   nil)
            _ (es-helpers/load-bulk (es-conn get-in-config) bulk-2)
            _ (with-atom-logger logger-1
                (sut/migrate-store-indexes {:migration-id "migration-test-4"
                                            :prefix       "0.0.0"
                                            :migrations   [:__test]
                                            :store-keys   [:relationship]
                                            :batch-size   100
                                            :buffer-size  3
                                            :confirm?     true
                                            :restart?     true}
                                           services))
            target-count-2 (ductile.doc/count-docs (es-conn get-in-config)
                                                   "v0.0.0_ctia_relationship"
                                                   nil)
            migration-state-2 (es-doc/get-doc (es-conn get-in-config)
                                              (migration-index get-in-config)
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
            "migration process should complete the migration after restart")))))

(deftest migration-with-malformed-docs
 (with-each-fixtures identity app
  (testing "migration with malformed documents in store"
    (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
          {:keys [all-stores]} (helpers/get-service-map app :StoreService)
          services (app->MigrationStoreServices app)

          store-types [:malware :tool :incident]
          logger (atom [])
          bad-doc {:id 1
                   :hey "I"
                   :am "a"
                   :bad "document"}]
      (helpers/set-capabilities! app
                                 "foouser"
                                 ["foogroup"]
                                 "user"
                                 all-capabilities)
      (whoami-helpers/set-whoami-response app
                                          "45c1f5e3f05d0"
                                          "foouser"
                                          "foogroup"
                                          "user")
        ;; insert proper documents
        (rollover-post-bulk app all-stores)
        ;; insert malformed documents
        (doseq [store-type store-types]
          (es-doc/create-doc (es-conn get-in-config)
                             (str (get-in (es-props get-in-config) [store-type :indexname]) "-write")
                             (name store-type)
                             bad-doc
                             "true"))
        (with-atom-logger logger
          (sut/migrate-store-indexes {:migration-id "test-3"
                                      :prefix       "0.0.0"
                                      :migrations   [:__test]
                                      :store-keys   store-types
                                      :batch-size   10
                                      :buffer-size  3
                                      :confirm?     true
                                      :restart?     false}
                                     services))
        (let [messages (set @logger)
              migration-state (es-doc/get-doc (es-conn get-in-config)
                                              (migration-index get-in-config)
                                              "migration"
                                              "test-3"
                                              {})]
          (doseq [store-type store-types]
            (let [migrated-store (get-in migration-state [:stores store-type])
                  {:keys [source target]} migrated-store]
              (is (= (inc fixtures-nb) (:total source)))
              (is (= fixtures-nb (:migrated target))))
            (is (some #(str/starts-with? % (format "%s - Cannot migrate entity: {"
                                                   (name store-type)))
                      messages)
                (format "malformed %s was not logged" store-type))))))))


(deftest test-migrate-store-indexes
 (with-each-fixtures identity app
  ;; TODO clean data
  (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
        {:keys [all-stores]} (helpers/get-service-map app :StoreService)
        services (app->MigrationStoreServices app)]
    (helpers/set-capabilities! app
                               "foouser"
                               ["foogroup"]
                               "user"
                               all-capabilities)
    (whoami-helpers/set-whoami-response app
                                        "45c1f5e3f05d0"
                                        "foouser"
                                        "foogroup"
                                        "user")
    ;; insert proper documents
    (rollover-post-bulk app all-stores)
    (testing "migrate ES Stores test setup"
      (testing "simulate migrate es indexes shall not create any document"
        (sut/migrate-store-indexes {:migration-id "test-1"
                                    :prefix       "0.0.0"
                                    :migrations   [:0.4.16]
                                    :store-keys   (keys (all-stores))
                                    :batch-size   10
                                    :buffer-size  3
                                    :confirm?     false
                                    :restart?     false}
                                   services)

        (doseq [store (vals (all-stores))]
          (is (not (index-exists? store "0.0.0"))))
        (is (nil? (seq (es-doc/get-doc (es-conn get-in-config)
                                       (migration-index get-in-config)
                                       "migration"
                                       "test-1"
                                       {})))))))
  (testing "migrate es indexes"
    (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
          {:keys [all-stores]} (helpers/get-service-map app :StoreService)
          services (app->MigrationStoreServices app)
          logger (atom [])]
        (with-atom-logger logger
          (sut/migrate-store-indexes {:migration-id "test-2"
                                      :prefix       "0.0.0"
                                      :migrations   [:__test]
                                      :store-keys   (keys (all-stores))
                                      :batch-size   10
                                      :buffer-size  3
                                      :confirm?     true
                                      :restart?     false}
                                     services))
        (testing "shall generate a proper migration state"
          (let [migration-state (es-doc/get-doc (es-conn get-in-config)
                                                (migration-index get-in-config)
                                                "migration"
                                                "test-2"
                                                {})]
            (is (= (set (keys (all-stores)))
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
                (is (= source-size (:total source))
                    (str "source size match for " (:index source)))
                (is (not (nil? started)))
                (is (not (nil? completed)))
                (is (<= (:migrated target) (:total source)))
                (is (int? (:total source)))
                (is (= (:index target)
                       (prefixed-index (:index source) "0.0.0")))))))
        (testing "shall produce valid logs"
          (let [messages (set @logger)]
            (is (contains? messages "set batch size: 10"))
            (is (clojure.set/subset?
                 #{"campaign - finished migrating 100 documents"
                   "indicator - finished migrating 100 documents"
                   (format "event - finished migrating %s documents"
                           (+ 1900 updates-nb))
                   "actor - finished migrating 100 documents"
                   "asset - finished migrating 100 documents"
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
                   "identity-assertion - finished migrating 0 documents"
                   "attack-pattern - finished migrating 100 documents"
                   "malware - finished migrating 100 documents"
                   "target-record - finished migrating 100 documents"
                   "tool - finished migrating 100 documents"
                   "vulnerability - finished migrating 100 documents"
                   "weakness - finished migrating 100 documents" }
                 messages))))

        (testing "shall produce new indices with enough documents and the right transforms"
          (let [{:keys [default
                        asset
                        target-record
                        relationship
                        judgement
                        investigation
                        coa
                        tool
                        attack-pattern
                        malware
                        incident
                        indicator
                        campaign
                        sighting
                        casebook
                        actor
                        vulnerability
                        weakness]}
                (get-in-config [:ctia :store :es])
                date (Date.)
                index-date (.format (SimpleDateFormat. "yyyy.MM.dd") date)
                expected-event-indices {(format "v0.0.0_ctia_event-%s-000001" index-date)
                                        1000
                                        (format "v0.0.0_ctia_event-%s-000002" index-date)
                                        (+ 900 updates-nb)}
                expected-indices
                (->> #{relationship
                       target-record
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
                _ (es-index/refresh! (es-conn get-in-config))
                formatted-cat-indices (es-helpers/get-cat-indices (:host default)
                                                                  (:port default))
                result-indices (select-keys formatted-cat-indices
                                            (keys expected-indices))]
            (is (= expected-indices result-indices)
                (let [[only-expected only-result _]
                      (diff expected-indices result-indices)]
                  (format "only in expected ==> %s\nonly in result ==> %s"
                          only-expected
                          only-result)))
            (doseq [[index _]
                    expected-indices]
              (let [docs (->> (ductile.doc/search-docs (es-conn get-in-config) (name index) nil nil {})
                              :data
                              (map :groups))]
                (is (every? #(= ["migration-test"] %)
                            docs))))))
        (testing "restart migration shall properly handle inserts, updates and deletes"
          (let [;; retrieve the first 2 source indices for sighting store
                {:keys [host port]} (get-in-config [:ctia :store :es :default])
                [sighting-index-1 sighting-index-2]
                (->> (es-helpers/get-cat-indices host port)
                     keys
                     (map name)
                     (filter #(.contains ^String % "sighting"))
                     sort
                     (take 2))

                ;; retrieve source entity to update, in first position of first index
                es-sighting0 (-> (ductile.doc/query (es-conn get-in-config)
                                                    sighting-index-1
                                                    {:match_all {}}
                                                    {:sort_by "timestamp:asc"
                                                     :size 1})
                                 :data
                                 first)
                ;; retrieve source entity to update, in first position of second index
                es-sighting1 (-> (ductile.doc/query (es-conn get-in-config)
                                                    sighting-index-2
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
                es-sightings-1 (-> (ductile.doc/query (es-conn get-in-config)
                                                      sighting-index-1
                                                      {:match_all {}}
                                                      {:sort_by "timestamp:desc"
                                                       :limit 5})
                                   :data)
                ;; retrieve 5 source entities to delete, in last positions of second index
                es-sightings-2 (-> (ductile.doc/query (es-conn get-in-config)
                                                      sighting-index-2
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
            (POST-bulk app new-malwares)
            ;; modify entities in first and second source indices
            (PUT app
                 (format "ctia/sighting/%s" sighting0-id)
                 :body updated-sighting-body
                 :headers {"Authorization" "45c1f5e3f05d0"})
            (PUT app
                 (format "ctia/sighting/%s" sighting1-id)
                 :body updated-sighting-body
                 :headers {"Authorization" "45c1f5e3f05d0"})
            ;; delete entities from first and second source indices
            (doseq [sighting-id sighting-ids]
              (DELETE app
                      (format "ctia/sighting/%s" sighting-id)
                      :headers {"Authorization" "45c1f5e3f05d0"}))
            (sut/migrate-store-indexes {:migration-id "test-2"
                                        :prefix       "0.0.0"
                                        :migrations   [:__test]
                                        :store-keys   (keys (all-stores))
                                        ;; small batch to check proper delete paging
                                        :batch-size   2
                                        :buffer-size  1
                                        :confirm?     true
                                        :restart?     true}
                                       services)
            (let [migration-state (get-migration "test-2" (es-conn get-in-config) services)
                  malware-migration (get-in migration-state [:stores :malware])
                  sighting-migration (get-in migration-state [:stores :sighting])
                  malware-target-store (get-in malware-migration [:target :store])
                  {last-target-malwares :data} (fetch-batch malware-target-store 3 0 "desc" nil)
                  {:keys [conn indexname mapping]} (get-in sighting-migration [:target :store])
                  updated-sightings (-> (ductile.doc/query conn
                                                           indexname
                                                           (es-query/ids [sighting0-id sighting1-id])
                                                           {})
                                        :data)
                  get-deleted-sightings (-> (ductile.doc/query conn
                                                               indexname
                                                               (es-query/ids sighting-ids)
                                                               {})
                                            :data)]
              (is (= (repeat 3 "INSERTED") (map :description last-target-malwares))
                  "inserted malwares must be found in target malware store")

              (is (= (repeat 2 "UPDATED") (map :description updated-sightings))
                  "updated document must be updated in target stores")
              (is (empty? get-deleted-sightings)
                  "deleted document must not be in target stores"))))))))

(defn load-test-fn
  [app maximal?]
  ;; insert 20000 docs per entity-type
  (doseq [bundle (repeatedly 20 #(fixt/bundle 1000 maximal?))]
    (POST-bulk app bundle))
  (doseq [batch-size [1000 3000 6000 10000]]
    (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
          services (app->MigrationStoreServices app)

          total-docs (* (count @example-types) 20000)
          _ (println (format "===== migrating %s documents with batch size %s"
                             total-docs
                             batch-size))
          migration-id (format "test-load-%s" batch-size)
          prefix (format "test_load_%s" batch-size)
          start (System/currentTimeMillis)
          _ (sut/migrate-store-indexes {:migration-id migration-id
                                        :prefix       prefix
                                        :migrations   [:__test]
                                        :store-keys   (into [] example-types)
                                        :batch-size   batch-size
                                        :buffer-size  3
                                        :confirm?     true
                                        :restart?     false}
                                       services)
          end (System/currentTimeMillis)
          total (/ (- end start) 1000)
          doc-per-sec (/ total-docs total)
          migration-state (es-doc/get-doc (es-conn get-in-config)
                                          (migration-index get-in-config)
                                          "migration"
                                          migration-id
                                          {})]
      (println "total: " (float total))
      (println "documents per seconds: " (float doc-per-sec))
      (doseq [[_ state] (:stores migration-state)]
        (is (= 20000
               (get-in state [:source :total])
               (get-in state [:target :migrated]))))
      (es-index/delete! (es-conn get-in-config) (format "v%s*" prefix))
      (es-doc/delete-doc (es-conn get-in-config) "migration" migration-id "true")))
  (es-index/delete! (es-conn (helpers/current-get-in-config-fn app)) "ctia_*"))

;;(deftest ^:integration minimal-load-test
;; (with-each-fixtures identity app
;;  (testing "load testing with minimal entities"
;;    (println "load testing with minimal entities")
;;    (load-test-fn app false))))

;;(deftest ^:integration maximal-load-test
;; (with-each-fixtures identity app
;;  (testing "load testing with maximal entities"
;;    (println "load testing with maximal entities")
;;    (load-test-fn app true))))
