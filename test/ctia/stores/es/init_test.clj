(ns ctia.stores.es.init-test
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ctia.stores.es.init :as sut]
            [ctia.stores.es.mapping :as m]
            [ctia.stores.es.schemas :refer [ESConnServices ESConnState]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :as es-helpers
             :refer [->ESConnServices for-each-es-version basic-auth basic-auth-properties]]
            [ductile.index :as index]
            [ductile.document :as doc]
            [ductile.conn :as conn]
            [ductile.auth :as auth]
            [ductile.auth.api-key :refer [create-api-key!]]
            [clojure.test :refer [deftest testing is are use-fixtures]]
            [schema.core :as s]
            [ctia.entity.event.schemas :as es])
  (:import [java.util UUID]
           [clojure.lang ExceptionInfo]))

(defn gen-indexname []
  (str "ctia_init_test_sighting"
       (UUID/randomUUID)))

(defn write-alias [indexname]
  (str indexname "-write"))

(defn mk-props [indexname]
  {:entity :sighting
   :indexname indexname
   :refresh_interval "2s"
   :shards 2
   :replicas 1
   :mappings {:a 1 :b 2}
   :host "localhost"
   :port 9207
   :update-mappings true
   :update-settings true
   :refresh-mappings true
   :version 7
   :auth basic-auth})

(deftest init-store-conn-test
 (let [services (->ESConnServices)]
   (testing "init store conn should return a proper conn state with aliases"
     (let [indexname (gen-indexname)
           {:keys [index props config conn]}
           (sut/init-store-conn (mk-props indexname) services)]
       (is (= index indexname))
       (is (= (:write-index props) (write-alias indexname)))
       (is (= "http://localhost:9207" (:uri conn)))
       (is (= indexname
              (-> config :aliases keys first)))
       (is (= "2s" (get-in config [:settings :refresh_interval])))
       (is (= 1 (get-in config [:settings :number_of_replicas])))
       (is (= 2 (get-in config [:settings :number_of_shards])))
       (is (= {} (select-keys (:mappings config) [:a :b])))))
   (testing "init store conn should provide a proper ES connection"
     (let [indexname (gen-indexname)]
       (are [msg props expected-uri]
           (testing msg
             (is (= expected-uri
                    (-> (sut/init-store-conn props services)
                        :conn
                        :uri)))
             true)

         "default protocol is http"
         (mk-props indexname)
         "http://localhost:9207"

         "uri should respect given protocol"
         (assoc (mk-props indexname)
                :protocol :https)
         "https://localhost:9207"

         "uri should respect given protocol, host and port"
         (assoc (mk-props indexname)
                :protocol :https
                :port 9201
                :host "cisco.com")
         "https://cisco.com:9201")))))

(deftest update-settings!-test
  (doseq [version [7]]
    (let [services (->ESConnServices)
          indexname (str "ctia_malware" (UUID/randomUUID))
          initial-props {:entity :malware
                         :indexname indexname
                         :host "localhost"
                         :port (+ 9200 version)
                         :shards 5
                         :replicas 2
                         :refresh_interval "1s"
                         :version version
                         :auth basic-auth}
          ;; create index
          {:keys [conn]} (sut/init-es-conn! initial-props services)]
      (try
        (let [new-props (assoc initial-props
                               :shards 4
                               :replicas 1
                               :refresh_interval "2s")
              _ (sut/update-settings! (sut/init-store-conn new-props services))
              {:keys [refresh_interval
                      number_of_shards
                      number_of_replicas]} (-> (index/get conn indexname)
                                               first
                                               val
                                               :settings
                                               :index)]
          (is (= "5" number_of_shards)
              "the number of shards is a static parameter")
          (testing "dynamic parameters should be updated"
            (is (= "1" number_of_replicas))
            (is (= "2s" refresh_interval))))
        (finally
          (es-helpers/clean-es-state! conn (str indexname "*"))
          (conn/close conn))))))

(defn exceptional-system-exit [] (throw (ex-info (str `exceptional-system-exit) {::exceptional-system-exit true})))
(defn exceptional-system-exit? [e]
  (boolean
    (when (instance? clojure.lang.ExceptionInfo e)
      (-> e ex-data ::exceptional-system-exit))))

(deftest get-existing-indices-test
  (helpers/with-config-transformer
    #(assoc-in % [:ctia :task :ctia.task.update-index-state] true)
    (let [indexname (gen-indexname)]
      (for-each-es-version
        "get-existing-indices should retrieve existing indices if any."
        [7]
        #(index/delete! % (str indexname "*"))
        (let [test-fn (fn [msg
                           input-indexname
                           expected-successful?
                           expected-output]
                        (assert (boolean? expected-successful?))
                        (testing msg
                          (let [output (try (with-redefs [sut/system-exit-error exceptional-system-exit] ;;FIXME move to config
                                              (sut/get-existing-indices conn input-indexname))
                                            (catch Throwable e
                                              (cond-> e
                                                (not (exceptional-system-exit? e)) throw)))]
                            (if expected-successful?
                              (is (= expected-output (set (keys output))))
                              (is (exceptional-system-exit? output))))))

              _ (test-fn "0 existing index"
                         indexname
                         true
                         #{})

              _ (index/create! conn indexname {})
              _ (test-fn "1 existing index with the exact name"
                         indexname
                         true
                         #{(keyword indexname)})

              indexname-with-date (str indexname "-2020.07.31")
              _ (index/create! conn indexname-with-date {})
              _ (test-fn "2 existing indices, 1 with exact name, 1 suffixed with date"
                         indexname
                         true
                         #{(keyword indexname-with-date)
                           (keyword indexname)})

              ;; generate an indexname which is a prefix of indexname
              ambiguous-indexname (->> (count indexname)
                                       dec
                                       (subs indexname 0))]
          (test-fn "CTIA must fail to start with configuration having ambiguous index names between stores"
                   ambiguous-indexname
                   false
                   nil))))))

(deftest init-es-conn!-test
  (helpers/with-config-transformer
    #(assoc-in % [:ctia :task :ctia.task.update-index-state] true)
    (let [indexname (gen-indexname)
          prepare-props (fn [props version]
                          (assoc props
                                 :version version
                                 :port (+ 9200 version)))]
      (for-each-es-version
        "get-existing-indices should retrieve existing indices if any."
        [7]
        #(es-helpers/clean-es-state! % (str indexname "*"))
        (testing "update mapping should allow adding fields or identical mapping"
          (let [services (->ESConnServices)
                props (prepare-props (mk-props indexname) version)
                test-fn (fn [msg expected-successful? field field-mapping]
                          ;; init and create aliased indices
                          (testing msg
                            (let [{:keys [conn]} (sut/init-es-conn! props services)]
                              (try
                                (let [output (try (with-redefs [sut/system-exit-error exceptional-system-exit ;;FIXME move to config
                                                                ;; redef mappings
                                                                sut/entity-fields
                                                                (cond-> sut/entity-fields
                                                                  field (assoc-in [:sighting :es-mapping "sighting" :properties field]
                                                                                  field-mapping))]
                                                    ;; init again to trigger mapping update
                                                    (sut/init-es-conn! props services))
                                                  (catch Throwable e
                                                    (cond-> e
                                                      (not (exceptional-system-exit? e)) throw)))]
                                  (is (= expected-successful? (not (exceptional-system-exit? output)))))
                                (finally
                                  ;; reset state
                                  (es-helpers/clean-es-state! conn (str indexname "ctia_*"))
                                  (conn/close conn))))))]
            (test-fn "update mapping should not fail on unchanged mapping"
                     true nil nil)
            (test-fn "update mapping should not fail on field addition"
                     true :new-field m/token)
            (test-fn "Update mapping fails when modifying existing field mapping and CTIA must not start in that case."
                     false :id m/text)))

        (testing "init-es-conn! should return a proper conn state and create an initial aliased index"
          (let [services (->ESConnServices)
                {:keys [index props config conn]}
                (-> (prepare-props (mk-props indexname) version)
                    (sut/init-es-conn! services))]
            (try
              (let [existing-index (index/get conn (str indexname "*"))
                    created-aliases (->> existing-index
                                         vals
                                         first
                                         :aliases
                                         keys
                                         set)]
                (is (= #{(keyword indexname) (keyword (write-alias indexname))}
                       created-aliases))
                (is (= index indexname))
                (is (= (:write-index props) (write-alias indexname)))
                (is (= (str "http://localhost:920" version) (:uri conn)))
                (is (= indexname
                       (-> config :aliases keys first)))
                (is (= "2s" (get-in config [:settings :refresh_interval])))
                (is (= 1 (get-in config [:settings :number_of_replicas])))
                (is (= 2 (get-in config [:settings :number_of_shards])))
                (is (= {} (select-keys (:mappings config) [:a :b]))))
              (finally
                #(es-helpers/clean-es-state! conn (str indexname "*"))
                (conn/close conn)))))))))

(deftest update-index-state-test
  (let [test-fn (fn [{:keys [update-mappings
                             update-settings
                             refresh-mappings]
                      :as props}]
                  (let [updated-mappings (atom false)
                        updated-template (atom false)
                        updated-settings (atom false)
                        refreshed-mappings (atom false)
                        stubs {:update-mappings! (fn [_c] (reset! updated-mappings true))
                               :update-settings! (fn [_c] (reset! updated-settings true))
                               :upsert-template! (fn [_c] (reset! updated-template true))
                               :refresh-mappings! (fn [_c] (reset! refreshed-mappings true))}]
                    (sut/update-index-state {:props props} stubs)
                    (is (= @updated-mappings (boolean update-mappings)))
                    (is (= @updated-template (boolean update-mappings)))
                    (is (= @updated-settings (boolean update-settings)))
                    (is (= @refreshed-mappings
                           (every? identity [update-mappings refresh-mappings])))))]
    (doseq [update-mappings?  [true false nil]
            update-settings?  [true false nil]
            refresh-mappings? [true false nil]
            :let [indexname (gen-indexname)]]
      (test-fn (assoc (mk-props indexname)
                      :update-mappings update-mappings?
                      :update-settings update-settings?
                      :refresh-mappings refresh-mappings?)))))

(deftest es-auth-properties-test
  (for-each-es-version
    "init-es-conn! should return a conn state in respect with given auth properties."
    [7] ;; auth only available on ES7 docker, use this macro to easily test future major versions
    #(index/delete! % "ctia*")
    (let [;; create API Key
          {key-id :id :keys [api_key]} (create-api-key! conn {:name "my-api-key"})
          api-key-params {:id key-id :api-key api_key}
          ok-api-key-auth-params {:type :api-key
                                  :params api-key-params}
          ko-api-key-auth-params {:type :api-key
                                  :params (assoc api-key-params :id "invalid id")}

          header-params (:headers (auth/api-key-auth api-key-params))
          ok-header-auth-params {:type :headers
                                 :params header-params}

          ko-header-auth-params {:type :headers
                                 :params {:authorization "invalid key"}}
          try-store (fn [store] (-> store first :state :conn
                                    (index/get-template "*")
                                    map?))
          try-auth-params (s/fn [auth-params authorized? :- s/Bool]
                            (helpers/with-config-transformer
                              #(assoc-in % [:ctia :store :es :default :auth] auth-params)
                              (if authorized?
                                ;; every store should be accessible
                                (helpers/fixture-ctia-with-app
                                  (fn [app]
                                    (let [{:keys [all-stores]} (helpers/get-service-map app :StoreService)
                                          stores-map (all-stores)
                                          _ (assert (seq stores-map))]
                                      (doseq [[k store] stores-map]
                                        (testing (pr-str k)
                                          (is (try-store store)))))))
                                ;; ctia should fail to initialize
                                (try (helpers/fixture-ctia-with-app identity)
                                     (is false "Expected error, actual none")
                                     (catch ExceptionInfo e
                                       (is (string/starts-with? (.getMessage e) "Unauthorized ES Request")))))))]
      (doseq [[auth-params authorized?] [[basic-auth true]
                                         [ok-api-key-auth-params true]
                                         [ok-header-auth-params true]
                                         [ko-api-key-auth-params false]
                                         [ko-header-auth-params false]]]
        (testing (format "auth-params: %s, authorized?: %s" auth-params authorized?)
          (try-auth-params auth-params authorized?))))))

(deftest mk-policy-test
  (let [test-plan [{:msg "mk-policy uses configured rollover."
                    :store-config {:rollover {:max_docs 500}}
                    :expected {:max_docs 500}}
                   {:msg "mk-policy uses default rollover when not configured."
                    :store-config {}
                    :expected sut/default-rollover}]]
    (doseq [{:keys [store-config expected]} test-plan]
      (is (= {:phases {:hot {:actions {:rollover expected}}}}
             (sut/mk-policy store-config))))))

(deftest mk-index-ilm-config-test
  (let [services (->ESConnServices)
        indexname (gen-indexname)
        base-props {:entity :sighting
                    :indexname indexname
                    :refresh_interval "2s"
                    :shards 2
                    :replicas 1
                    :mappings {:a 1 :b 2}
                    :host "localhost"
                    :port 9207
                    :version 7
                    :auth basic-auth}
        test-cases [{:message "rollover with max_docs"
                     :expected {:rollover {:max_docs 1000}}
                     :store-props (assoc base-props :rollover {:max_docs 1000})}]]
    (doseq [{:keys [message expected store-props]} test-cases]
      (let [store-config (sut/init-store-conn store-props services)
            ilm-index-config (sut/mk-index-ilm-config store-config)
            {:keys [policy template aliases settings mappings]} (:config ilm-index-config)
            template-settings (get-in template [:template :settings])]
        (testing message
          (is (= (dissoc ilm-index-config :config)
                 (dissoc store-config :config)))
          (is (= {:phases {:hot {:actions {:rollover (:rollover store-props)}}}}
                 policy))
          (is (= (str (:indexname store-props) "*") (:index_patterns template)))
          (is (map? (:template template)))
          (is (map? settings))
          (is (map? mappings))
          (is (= mappings (get-in template [:template :mappings])))
          (is (= settings (get-in template [:template :settings])))
          (is (= aliases
                 {indexname {}
                  (str indexname "-write") {:is_write_index true}}))
          (is (= (:shards store-props) (:number_of_shards template-settings)))
          (is (= (:replicas store-props) (:number_of_replicas template-settings)))
          (is (= (:refresh_interval store-props) (:refresh_interval template-settings)))
          (is (= {:name (:indexname store-props)
                  :rollover_alias (str indexname "-write")}
                 (get-in settings [:index :lifecycle])))
          (is (= {:name (:indexname store-props)
                  :rollover_alias (str (:indexname store-props) "-write")}
                 (get-in template-settings [:index :lifecycle]))))))))

(s/defn legacy-init-es-conn! :- ESConnState
  "initiate an ES Store connection,
   put the index template, return an ESConnState"
  [properties :- sut/StoreProperties
   {{:keys [get-in-config]} :ConfigService
    :as services} :- ESConnServices]
  (let [{:keys [conn index props config] :as conn-state}
        (sut/init-store-conn
         (dissoc properties :migrate-to-ilm)
         services)
        existing-indices (sut/get-existing-indices conn index)]
    (when-not (seq existing-indices)
      (index/create-template! conn index config)
      (log/infof "updated template: %s" index))
    (when (empty? existing-indices)
      ;;https://github.com/elastic/elasticsearch/pull/34499
      (index/create! conn
                     (format "<%s-{now/d}-000001>" index)
                     (update config :aliases assoc (:write-index props) {})))
      conn-state))

(defn force-n-rollover!
  [{:keys [conn props]} n]
  (doseq [i (range n)]
    (ductile.index/rollover! conn (:write-index props) {:max_docs 0})))

(defn check-ilm-migration
  [{:keys [conn index props] :as _store-conn}]
  (let [updated-indices (sort-by first (index/get conn index))
        write-index (:write-index props)
        [_ real-write-index-updated] (last updated-indices)
        rollover (:rollover props sut/default-rollover)]
    (assert (seq rollover))
    (assert (< 1 (count updated-indices)))
    (doseq [[_ real-index-updated] (butlast updated-indices)]
      (is (= {:ctia_ilm_migration_sighting {}}
             (:aliases real-index-updated))
          "all indices but the write index should only have the read index"))
    (is (= {:ctia_ilm_migration_sighting {},
            :ctia_ilm_migration_sighting-write {:is_write_index true}}
           (:aliases real-write-index-updated))
        "current write index should have write alias updated with is_write_index")
    (is (= rollover
           (get-in (index/get-policy conn index)
                   [(keyword index) :policy  :phases :hot :actions :rollover]))
        "Policy should be created.")
    (doseq [[_ real-index-updated] updated-indices]
      (is (= {:name index :rollover_alias write-index}
             (get-in real-index-updated [:settings :index :lifecycle]))
          "lifecycle must be added to all indices"))
    (is (nil? (index/get-template conn index))
        "Legacy template must be deleted")
    (is (seq (index/get-index-template conn index))
        "Index template must be created")))

(deftest update-ilm-settings!-test
  (let [indexname "ctia_ilm_migration_sighting"
        prepare-props (fn [props version]
                        (assoc props
                               :version version
                               :port (+ 9200 version)))]
    (for-each-es-version
      "migrate to ilm"
      [7]
      #(es-helpers/clean-es-state! % (str indexname "*"))
      (let [services (->ESConnServices)
            props (prepare-props (mk-props indexname) version)
            legacy-store-conn (legacy-init-es-conn! props services)
            _ (force-n-rollover! legacy-store-conn 4)
            store-conn (sut/init-es-conn! props services)
            _ (is (true? (sut/update-ilm-settings! store-conn)))]
        (check-ilm-migration store-conn)))))

(deftest upddate-index-state-task-for-ilm-test
  (helpers/with-config-transformer
    #(assoc-in % [:ctia :task :ctia.task.update-index-state] true)
    (let [indexname "ctia_ilm_migration_sighting"
          prepare-props (fn [props version]
                          (assoc props
                                 :version version
                                 :port (+ 9200 version)))]
      (for-each-es-version
        "migrate to ilm with update task"
        [7]
        #(es-helpers/clean-es-state! % (str indexname "*"))
        (let [services (->ESConnServices)
              base-props (prepare-props (mk-props indexname) version)
              ;; init legacy state and rollover
              {legacy-conn :conn legacy-index :index :as legacy-store-conn}
              (legacy-init-es-conn! base-props services)
              _ (force-n-rollover! legacy-store-conn 4)
              _ (assert (= 5
                           (count (index/get legacy-conn
                                             legacy-index))))
              _ (assert (seq (index/get-template legacy-conn legacy-index)))

              ;; shorter lifecycle poll interval for test purpose
              _ (es-helpers/update-cluster-lifecycle-poll_interval conn "1s")

              ;; restart CTIA with migrate-to-ilm
              ilm-migration-props (assoc base-props
                                         :migrate-to-ilm true
                                         :update-mappings false
                                         :update-settings false
                                         :refresh-mappings false
                                         :rollover {:max_docs 1}
                                         )
              {:keys [conn index props] :as store-conn}
              (sut/init-es-conn! ilm-migration-props services)]
          (check-ilm-migration store-conn)
          ;; check that ILM properly rollover
          ;; max_docs is set to 1 and indices.lifecycle.poll_interval to 1s in ES container
          ;; we create 1 doc, wait long enough to let the rollover to be triggered and the index created
          (doc/create-doc conn (:write-index props) {:id "doc-1"} {:refresh "true"})
          (Thread/sleep 5000)
          (is (= 6 (count (index/get conn index))))
          (es-helpers/update-cluster-lifecycle-poll_interval conn "10m"))))))

(deftest init-with-legacy-state-without-update
  ;; this whole test is about ensuring
  ;; that nothing is created / modified
  ;; outside update index state task
  (helpers/with-config-transformer
    #(assoc-in % [:ctia :task :ctia.task.update-index-state] false)
    (let [indexname "ctia_ilm_migration_sighting"
          prepare-props (fn [props version]
                          (assoc props
                                 :version version
                                 :port (+ 9200 version)))]
      (for-each-es-version
        "check that we can restart the app on legacy indices without migrating"
        [7]
        #(es-helpers/clean-es-state! % (str indexname "*"))
        (let [services (->ESConnServices)
              base-props (prepare-props (mk-props indexname) version)
              ;; init legacy state and rollover
              {legacy-conn :conn legacy-index :index :as legacy-store-conn}
              (legacy-init-es-conn! base-props services)
              _ (force-n-rollover! legacy-store-conn 4)
              legacy-template (index/get-template legacy-conn legacy-index)
              legacy-indices (index/get legacy-conn legacy-index)
              _ (assert (= 5
                           (count (index/get legacy-conn
                                             legacy-index))))
              _ (assert (seq (index/get-template legacy-conn legacy-index)))
              ;; CTIA restarts with new init process before we migrate old indices to ILM
              {not-migrated-conn :conn not-migrated-index :index
               :as not-migrated-store-conn}
              (sut/init-es-conn! base-props services)
              not-migrated-template (index/get-template not-migrated-conn not-migrated-index)
              not-migrated-indices (index/get not-migrated-conn not-migrated-index)]
          (is (= legacy-indices not-migrated-indices))
          (is (= legacy-template not-migrated-template))
          (is (nil? (index/get-policy not-migrated-conn not-migrated-index))
              "policy should not been created if index already exist and update-index-state is false")
          (is (nil? (index/get-index-template not-migrated-conn not-migrated-index))
              "index-template should not been created if index already exist and update-index-state is false"))))))
