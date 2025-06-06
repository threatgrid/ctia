(ns ctia.task.migration.migrate-es-stores-test
  (:require [clj-momo.lib.clj-time.coerce :as time-coerce]
            [clj-momo.test-helpers.core :as mth]
            [cheshire.core :as json]
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
           [java.util Date UUID]))


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
          (es-helpers/clean-es-state! "v0.0.0*")
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
                          :confirm? true
                          :restart? false}]
    (testing "misconfigured migration"
      (let [investigation-indexname (str "v1.2.0_ctia_investigation" (UUID/randomUUID))
            malware-indexname (str "v1.2.0_ctia_malware" (UUID/randomUUID))]
        (with-each-fixtures #(-> %
                                 (assoc-in [:ctia :store :es :investigation :indexname]
                                           investigation-indexname)
                                 (assoc-in [:malware 0 :state :props :indexname] malware-indexname))
          app
          (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
            (let [v (get-in-config [:ctia :store :es :investigation :indexname])]
              (assert (= v investigation-indexname)
                      [v investigation-indexname]))
            (let [v (get-in-config [:malware 0 :state :props :indexname])]
              (assert (= v malware-indexname)
                      [v malware-indexname]))
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
                                                     get-in-config)))))))
      (testing "properly configured migration"
        (with-each-fixtures identity app
          (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
            (is (sut/check-migration-params migration-params
                                            get-in-config)))))

      (testing "different target cluster same indexname"
        (let [malware-indexname (str "v1.2.0_ctia_malware" (UUID/randomUUID))
              target-store {:host "another-host-cluster"
                            :auth {:type :basic-auth
                                   :params {:user "basic"
                                            :pwd "ductile"}}}]
          (with-each-fixtures #(assoc-in % [:ctia :store :es :malware :indexname] malware-indexname)
            app
            (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
              (is (sut/check-migration-params (assoc (assoc migration-params :store-keys [:malware])
                                                     :store {:es {:malware target-store}})
                                              get-in-config))
              (is (sut/check-migration-params (assoc (assoc migration-params :store-keys [:malware])
                                                     :store {:es {:default target-store}})
                                              get-in-config)
                  "configured default target store shall be considered")))))))

(deftest prepare-params-test
  (let [migration-props {:batch-size 100,
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
          conn (es-conn get-in-config)
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
        (es-index/get conn
                      (str (get-in (es-props get-in-config) [store-type :indexname]) "*")))
      (sut/migrate-store-indexes {:migration-id "test-3"
                                  :prefix       "0.0.0"
                                  :migrations   [:__test]
                                  :store-keys   store-types
                                  :batch-size   10
                                  :confirm?     true
                                  :restart?     false}
                                 services)
      (let [migration-state (ductile.doc/get-doc conn
                                                 (migration-index get-in-config)
                                                 "migration"
                                                 "test-3"
                                                 {})]
          (doseq [store-type store-types]
            (is (= (count (es-index/get conn
                                        (str "v0.0.0_" (get-in (es-props get-in-config) [store-type :indexname]) "*")))
                   3)
                "target index should be rolled over during migration")
            (es-index/get conn
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
            conn (es-conn get-in-config)
            storemap {:conn conn
                      :indexname (es-helpers/get-indexname app :relationship)
                      :mapping "relationship"
                      :props {:write-index (es-helpers/get-indexname app :relationship)}
                      :type "relationship"
                      :settings {}
                      :config {}}
            docs (map (partial es-helpers/prepare-bulk-ops app)
                      (line-seq rdr))
            _ (es-helpers/load-bulk conn docs)
            no-meta-docs (map #(dissoc % :_index :_id)
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
            indexname (str "v0.0.1_" (es-helpers/get-indexname app :relationship))
            conn (es-conn get-in-config)
            storemap {:conn conn
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
                         :migration-es-conn conn
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
                                                                      conn
                                                                      services)
                                                       :stores
                                                       :relationship)
                            _ (es-index/refresh! conn)
                            migrated-docs (:data (ductile.doc/query conn
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
                 {:confirm? false})
        (es-helpers/clean-es-state! conn "v0.0.1_*")))))

(deftest sliced-migration-test
  (with-each-fixtures identity app
    (with-open [rdr (io/reader "./test/data/indices/sample-relationships-1000.json")]
      (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
            services (app->MigrationStoreServices app)
            conn (es-conn get-in-config)
            {wo-modified true
             w-modified false} (->> (line-seq rdr)
                                    (map (partial es-helpers/prepare-bulk-ops app))
                                    (group-by #(nil? (:modified %))))
            sorted-w-modified (sort-by :modified w-modified)
            bulk-1 (concat wo-modified (take 500 sorted-w-modified))
            bulk-2 (drop 500 sorted-w-modified)
            logger-1 (atom [])
            _ (es-helpers/load-bulk conn bulk-1)
            _ (with-atom-logger logger-1
                (sut/migrate-store-indexes {:migration-id "migration-test-4"
                                            :prefix       "0.0.0"
                                            :migrations   [:__test]
                                            :store-keys   [:relationship]
                                            :batch-size   100
                                            :confirm?     true
                                            :restart?     false}
                                           services))
            migration-state-1 (ductile.doc/get-doc conn
                                                   (migration-index get-in-config)
                                                   "migration"
                                                   "migration-test-4"
                                                   {})
            target-count-1 (ductile.doc/count-docs conn
                                                   (str "v0.0.0_"
                                                        (es-helpers/get-indexname app :relationship))
                                                   nil)
            _ (es-helpers/load-bulk conn bulk-2)
            _ (with-atom-logger logger-1
                (sut/migrate-store-indexes {:migration-id "migration-test-4"
                                            :prefix       "0.0.0"
                                            :migrations   [:__test]
                                            :store-keys   [:relationship]
                                            :batch-size   100
                                            :confirm?     true
                                            :restart?     true}
                                           services))
            target-count-2 (ductile.doc/count-docs conn
                                                   (str "v0.0.0_"
                                                        (es-helpers/get-indexname app :relationship))
                                                   nil)
            migration-state-2 (ductile.doc/get-doc conn
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
          conn (es-conn get-in-config)
          store-types [:malware :tool :incident]
          logger (atom [])
          bad-doc {:id "doc1"
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
          (ductile.doc/create-doc conn
                                  (str (get-in (es-props get-in-config) [store-type :indexname]) "-write")
                                  bad-doc
                                  {:refresh "true"}))
        (with-atom-logger logger
          (sut/migrate-store-indexes {:migration-id "test-3"
                                      :prefix       "0.0.0"
                                      :migrations   [:__test]
                                      :store-keys   store-types
                                      :batch-size   10
                                      :confirm?     true
                                      :restart?     false}
                                     services))
        (let [messages (set @logger)
              migration-state (ductile.doc/get-doc conn
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
        services (app->MigrationStoreServices app)
        conn (es-conn get-in-config)
        migrated-store-keys [:incident
                             :asset
                             :attack-pattern
                             :judgement
                             :sighting
                             :malware]]
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
                                    :store-keys   migrated-store-keys
                                    :batch-size   10
                                    :confirm?     false
                                    :restart?     false}
                                   services)

        (doseq [store (-> (all-stores) (select-keys migrated-store-keys) vals)]
          (is (not (index-exists? store "0.0.0"))))
        (is (nil? (seq (ductile.doc/get-doc conn
                                            (migration-index get-in-config)
                                            "migration"
                                            "test-1"
                                            {}))))))
    (testing "migrate es indexes"
      (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
            {:keys [all-stores]} (helpers/get-service-map app :StoreService)
            services (app->MigrationStoreServices app)
            conn (es-conn get-in-config)
            logger (atom [])]
        (with-atom-logger logger
          (sut/migrate-store-indexes {:migration-id "test-2"
                                      :prefix       "0.0.0"
                                      :migrations   [:__test]
                                      :store-keys   migrated-store-keys
                                      :batch-size   10
                                      :confirm?     true
                                      :restart?     false}
                                     services))
        (testing "shall generate a proper migration state"
          (let [migration-state (ductile.doc/get-doc conn
                                                     (migration-index get-in-config)
                                                     "migration"
                                                     "test-2"
                                                     {})]
            (is (= (set migrated-store-keys)
                   (set (keys (:stores migration-state)))))
            (doseq [[entity-type migrated-store] (:stores migration-state)]
              (let [{:keys [source target started completed]} migrated-store
                    source-size
                    (cond
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
            (is (set/subset?
                 #{"asset - finished migrating 100 documents"
                   "incident - finished migrating 100 documents"
                   "judgement - finished migrating 100 documents"
                   "sighting - finished migrating 100 documents"
                   "attack-pattern - finished migrating 100 documents"
                   "malware - finished migrating 100 documents"}
                 messages))))

        (testing "shall produce new indices with enough documents and the right transforms"
          (let [{:keys [default
                        asset
                        judgement
                        attack-pattern
                        malware
                        incident
                        sighting]}
                (get-in-config [:ctia :store :es])
                date (Date.)
                index-date (.format (SimpleDateFormat. "yyyy.MM.dd") date)
                expected-indices
                (keywordize-keys
                 (into {}
                       (map (fn [k]
                              {(format "v0.0.0_%s-%s-000001" (:indexname k) index-date) 50
                               (format "v0.0.0_%s-%s-000002" (:indexname k) index-date) 50
                               (format "v0.0.0_%s-%s-000003" (:indexname k) index-date) 0}))
                       #{asset
                         attack-pattern
                         incident
                         judgement
                         malware
                         sighting}))
                _ (es-index/refresh! conn)
                formatted-cat-indices (es-helpers/get-cat-indices conn)
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
              (let [docs (->> (ductile.doc/search-docs conn (name index) nil nil {})
                              :data
                              (map :groups))]
                (is (every? #(= ["migration-test"] %)
                            docs))))))
        (testing "restart migration shall properly handle inserts, updates and deletes"
          (let [;; retrieve the first 2 source indices for sighting store
                [sighting-index-1 sighting-index-2 :as sighting-indices]
                (->> (es-helpers/get-cat-indices conn)
                     keys
                     (map name)
                     (filter #(.contains ^String % "sighting"))
                     sort
                     (take 2))
                _ (assert (= 2 (count sighting-indices)) sighting-indices)

                ;; retrieve source entity to update, in first position of first index
                es-sighting0 (-> (ductile.doc/query conn
                                                    sighting-index-1
                                                    {:match_all {}}
                                                    {:sort_by "timestamp:asc"
                                                     :size 1})
                                 :data
                                 first)
                ;; retrieve source entity to update, in first position of second index
                es-sighting1 (-> (ductile.doc/query conn
                                                    sighting-index-2
                                                    {:match_all {}}
                                                    {:sort_by "timestamp:asc"
                                                     :size 1})
                                 :data
                                 first)

                ;; prepare new malwares
                new-malwares {:malwares (->> (fixt/n-examples :malware 3 false)
                                             (map #(assoc % :description "INSERTED")))}

                ;; retrieve 5 source entities to delete, in last positions of first index
                es-sightings-1 (-> (ductile.doc/query conn
                                                      sighting-index-1
                                                      {:match_all {}}
                                                      {:sort_by "timestamp:desc"
                                                       :limit 5})
                                   :data)
                ;; retrieve 5 source entities to delete, in last positions of second index
                es-sightings-2 (-> (ductile.doc/query conn
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
            (let [response (PUT app
                               (format "ctia/sighting/%s" sighting0-id)
                             :body updated-sighting-body
                             :headers {"Authorization" "45c1f5e3f05d0"})]
              (is (= 200 (:status response))
                  response))
            (let [response (PUT app
                               (format "ctia/sighting/%s" sighting1-id)
                             :body updated-sighting-body
                             :headers {"Authorization" "45c1f5e3f05d0"})]
              (is (= 200 (:status response))
                  response))
            ;; delete entities from first and second source indices
            (doseq [sighting-id sighting-ids]
              (let [response (DELETE app
                                 (format "ctia/sighting/%s" sighting-id)
                               :headers {"Authorization" "45c1f5e3f05d0"})]
                (is (= 204 (:status response))
                    response)))
            (sut/migrate-store-indexes {:migration-id "test-2"
                                        :prefix       "0.0.0"
                                        :migrations   [:__test]
                                        :store-keys   migrated-store-keys
                                        ;; small batch to check proper delete paging
                                        :batch-size   2
                                        :confirm?     true
                                        :restart?     true}
                                       services)
            (let [migration-state (get-migration "test-2" conn services)
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
                  "deleted document must not be in target stores")))))))))

(defn load-test-fn
  [app maximal?]
  ;; insert 20000 docs per entity-type
  (try
    (doseq [bundle (repeatedly 20 #(fixt/bundle 1000 maximal?))]
      (POST-bulk app bundle))
    (doseq [batch-size [1000 3000 6000 10000]
            :let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)
                  services (app->MigrationStoreServices app)
                  conn (es-conn get-in-config)
                  migration-id (format "test-load-%s" batch-size)
                  prefix (format "test_load_%s" batch-size)]]
      (try
        (let [total-docs (* (count @example-types) 20000)
              _ (println (format "===== migrating %s documents with batch size %s"
                                 total-docs
                                 batch-size))
              start (System/currentTimeMillis)
              _ (sut/migrate-store-indexes {:migration-id migration-id
                                            :prefix       prefix
                                            :migrations   [:__test]
                                            :store-keys   (into [] example-types)
                                            :batch-size   batch-size
                                            :confirm?     true
                                            :restart?     false}
                                           services)
              end (System/currentTimeMillis)
              total (/ (- end start) 1000)
              doc-per-sec (/ total-docs total)
              migration-state (ductile.doc/get-doc conn
                                                   (migration-index get-in-config)
                                                   "migration"
                                                   migration-id
                                                   {})]
          (println "total: " (float total))
          (println "documents per seconds: " (float doc-per-sec))
          (doseq [[_ state] (:stores migration-state)]
            (is (= 20000
                   (get-in state [:source :total])
                   (get-in state [:target :migrated])))))
        (finally
          (es-index/delete! conn (format "v%s*" prefix))
          (ductile.doc/delete-doc conn "migration" migration-id {:refresh "true"}))))
    (finally
      (es-index/delete! (es-conn (helpers/current-get-in-config-fn app)) "ctia_*"))))

(deftest extract-jackson-config-test
  (is (nil?
        (sut/extract-jackson-config
          {:restart true
           :confirm true})))
  (is (= {:maxStringLength 10
          :maxNumberLength 11
          :maxNestingDepth 12}
         (sut/extract-jackson-config
           {:restart true
            :confirm true
            :jackson-maxStringLength 10
            :jackson-maxNumberLength 11
            :jackson-maxNestingDepth 12}))))

(deftest prep-run-migration-options-test
  (is (= 0
         (sut/prep-run-migration-options ["--help"])
         (sut/prep-run-migration-options ["-h"])))
  (is (= {:confirm? nil :restart? nil} (sut/prep-run-migration-options [])))
  (is (= 1 (sut/prep-run-migration-options ["--jackson-maxStringLength"])))
  (is (= 1 (sut/prep-run-migration-options ["--jackson-maxStringLength" "true"])))
  (is (= {:confirm? true,
          :restart? true,
          :jackson-config {:maxNestingDepth 10, :maxNumberLength 500, :maxStringLength 250000}}
         (sut/prep-run-migration-options ["--confirm"
                                          "--restart"
                                          "--jackson-maxNestingDepth" "10"
                                          "--jackson-maxStringLength" "250000"
                                          "--jackson-maxNumberLength" "500"]))))

(deftest wrap-jackson-config-test
  (testing "maxStringLength"
    (is (= "123456789"
           ((sut/wrap-jackson-config json/parse-stream-strict
                                     {:maxStringLength 10})
            (java.io.StringReader. (pr-str "123456789")))))
    (is (thrown? Exception
                 ((sut/wrap-jackson-config json/parse-stream-strict
                                           {:maxStringLength 5})
                  (java.io.StringReader. (pr-str "123456789"))))))
  (testing "maxNumberLength"
    (is (= 123456789
           ((sut/wrap-jackson-config json/parse-stream-strict
                                     {:maxNumberLength 10})
            (java.io.StringReader. "123456789"))))
    (is (thrown? Exception
                 ((sut/wrap-jackson-config json/parse-stream-strict
                                           {:maxNumberLength 5})
                  (java.io.StringReader. "123456789")))))
  (testing "maxNestingDepth"
    (is (= [[[[[1]]]]]
           ((sut/wrap-jackson-config json/parse-stream-strict
                                     {:maxNestingDepth 10})
            (java.io.StringReader. "[[[[[1]]]]]"))))
    (is (thrown? Exception
                 ((sut/wrap-jackson-config json/parse-stream-strict
                                           {:maxNestingDepth 3})
                  (java.io.StringReader. "[[[[[1]]]]]"))))))

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
