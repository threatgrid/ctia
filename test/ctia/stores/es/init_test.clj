(ns ctia.stores.es.init-test
  (:require [clojure.string :as string]
            [ctia.stores.es.init :as sut]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :refer [->ESConnServices for-each-es-version basic-auth basic-auth-properties]]
            [ctia.stores.es.mapping :as m]
            [ductile.index :as index]
            [ductile.conn :as conn]
            [ductile.auth :as auth]
            [ductile.auth.api-key :refer [create-api-key!]]
            [clojure.test :refer [deftest testing is are use-fixtures]]
            [clj-momo.test-helpers.core :as mth]
            [schema.core :as s])
  (:import [java.util UUID]
           [clojure.lang ExceptionInfo]))

#_ ;;FIXME incompatible with this ns
(use-fixtures :once mth/fixture-schema-validation)

(defn gen-indexname []
  (str "ctia_init_test_sighting"
       (UUID/randomUUID)))

(defn write-alias [indexname]
  (str indexname "-write"))

(defn props-aliased [indexname]
  {:entity :sighting
   :indexname indexname
   :refresh_interval "2s"
   :shards 2
   :replicas 1
   :mappings {:a 1 :b 2}
   :host "localhost"
   :port 9207
   :aliased true
   :update-mappings true
   :update-settings true
   :refresh-mappings true
   :version 7
   :auth basic-auth})

(defn props-not-aliased [indexname]
  {:entity :sighting
   :indexname indexname
   :shards 2
   :replicas 1
   :mappings {:a 1 :b 2}
   :host "localhost"
   :port 9207
   :aliased false
   :update-mappings true
   :update-settings true
   :version 7
   :auth basic-auth})

(deftest init-store-conn-test
 (let [services (->ESConnServices)]
   (testing "init store conn should return a proper conn state with unaliased conf"
     (let [indexname (gen-indexname)
           base-props (props-not-aliased indexname)
           {:keys [index props config conn]}
           (sut/init-store-conn base-props services)]
       (is (= index indexname))
       (is (= (:write-index props) indexname))
       (is (= "http://localhost:9207" (:uri conn)))
       (is (nil? (:aliases config)))
       (is (= "1s" (get-in config [:settings :refresh_interval])))
       (is (= 1 (get-in config [:settings :number_of_replicas])))
       (is (= 2 (get-in config [:settings :number_of_shards])))
       (is (= {} (select-keys (:mappings config) [:a :b])))))

   (testing "init store conn should return a proper conn state with aliased conf"
     (let [indexname (gen-indexname)
           {:keys [index props config conn]}
           (sut/init-store-conn (props-aliased indexname) services)]
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
         (props-aliased indexname)
         "http://localhost:9207"

         "uri should respect given protocol"
         (assoc (props-aliased indexname)
                :protocol :https)
         "https://localhost:9207"

         "uri should respect given protocol, host and port"
         (assoc (props-aliased indexname)
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
                         :aliased true
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
          (index/delete! conn (str indexname "*"))
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
                              (is (= expected-output output))
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
          clean-template #(index/delete-template! % indexname)
          clean-index #(index/delete! % (str indexname "*"))
          clean-all #(do (clean-index %) (clean-template %))
          prepare-props (fn [props version]
                          (assoc props
                                 :version version
                                 :port (+ 9200 version)))]
      (for-each-es-version
        "get-existing-indices should retrieve existing indices if any."
        [7]
        clean-index
        (testing "init-es-conn! should return a proper conn state with unaliased conf, but not create any index"
          (let [services (->ESConnServices)
                props (prepare-props (props-not-aliased indexname) version)
                {:keys [index props config conn]} (sut/init-es-conn! props services)]
            (try
              (let [existing-index (index/get conn (str indexname "*"))]
                (is (empty? existing-index))
                (is (= index indexname))
                (is (= (:write-index props) indexname))
                (is (= (str "http://localhost:920" version) (:uri conn)))
                (is (nil? (:aliases config)))
                (is (= "1s" (get-in config [:settings :refresh_interval])))
                (is (= 1 (get-in config [:settings :number_of_replicas])))
                (is (= 2 (get-in config [:settings :number_of_shards])))
                (is (= {} (select-keys (:mappings config) [:a :b]))))
              (finally
                (clean-all conn)
                (conn/close conn)))))

        (testing "update mapping should allow adding fields or identical mapping"
          (let [services (->ESConnServices)
                props (prepare-props (props-aliased indexname) version)
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
                                  (clean-all conn)
                                  (conn/close conn))))))]
            (test-fn "update mapping should not fail on unchanged mapping"
                     true nil nil)
            (test-fn "update mapping should not fail on field addition"
                     true :new-field m/token)
            (test-fn "Update mapping fails when modifying existing field mapping and CTIA must not start in that case."
                     false :id m/text)))

        (testing "init-es-conn! should return a proper conn state with aliased conf, and create an initial aliased index"
          (let [services (->ESConnServices)
                {:keys [index props config conn]}
                (-> (prepare-props (props-aliased indexname) version)
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
                (clean-all conn)
                (conn/close conn)))))

        (testing "init-es-conn! should return a conn state that ignore aliased conf setting when an unaliased index already exists"
          (index/create! conn
                         indexname
                         {:settings m/store-settings})
          (let [services (->ESConnServices)
                {:keys [index props config conn]}
                (-> (prepare-props (props-aliased indexname) version)
                    (sut/init-es-conn! services))
                existing-index (index/get conn (str indexname "*"))
                created-aliases (->> existing-index
                                     vals
                                     first
                                     :aliases
                                     keys
                                     set)]
            (is (= #{} created-aliases))
            (is (= index indexname))
            (is (= (:write-index props) indexname))
            (is (= (str "http://localhost:920" version) (:uri conn)))
            (is (= indexname
                   (-> config :aliases keys first)))
            (is (= 1 (get-in config [:settings :number_of_replicas])))
            (is (= 2 (get-in config [:settings :number_of_shards])))
            (is (= {} (select-keys (:mappings config) [:a :b])))))))))

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
      (test-fn (assoc (props-aliased indexname)
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
