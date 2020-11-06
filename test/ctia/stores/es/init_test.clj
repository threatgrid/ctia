(ns ctia.stores.es.init-test
  (:require [ctia.stores.es.init :as sut]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :refer [->ESConnServices for-each-es-version]]
            [clj-http.client :as http]
            [ctia.stores.es.mapping :as m]
            [ductile
             [index :as index]
             [conn :as conn]]
            [clojure.test :refer [deftest testing is are]]))

(def indexname "ctia_init_test_sighting")
(def write-alias (str indexname "-write"))
(def props-aliased {:entity :sighting
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
                    :version 7})

(def props-not-aliased {:entity :sighting
                        :indexname indexname
                        :shards 2
                        :replicas 1
                        :mappings {:a 1 :b 2}
                        :host "localhost"
                        :port 9207
                        :aliased false
                        :update-mappings true
                        :update-settings true
                        :version 7})

(deftest init-store-conn-test
 (let [services (->ESConnServices)]
   (testing "init store conn should return a proper conn state with unaliased conf"
     (let [{:keys [index props config conn]}
           (sut/init-store-conn props-not-aliased services)]
       (is (= index indexname))
       (is (= (:write-index props) indexname))
       (is (= "http://localhost:9207" (:uri conn)))
       (is (nil? (:aliases config)))
       (is (= "1s" (get-in config [:settings :refresh_interval])))
       (is (= 1 (get-in config [:settings :number_of_replicas])))
       (is (= 2 (get-in config [:settings :number_of_shards])))
       (is (= {} (select-keys (:mappings config) [:a :b])))))

   (testing "init store conn should return a proper conn state with aliased conf"
     (let [{:keys [index props config conn]}
           (sut/init-store-conn props-aliased services)]
       (is (= index indexname))
       (is (= (:write-index props) write-alias))
       (is (= "http://localhost:9207" (:uri conn)))
       (is (= indexname
              (-> config :aliases keys first)))
       (is (= "2s" (get-in config [:settings :refresh_interval])))
       (is (= 1 (get-in config [:settings :number_of_replicas])))
       (is (= 2 (get-in config [:settings :number_of_shards])))
       (is (= {} (select-keys (:mappings config) [:a :b])))))
   (testing "init store conn should provide a proper ES connection"
     (are [msg props expected-uri]
         (testing msg
           (is (= expected-uri
                  (-> (sut/init-store-conn props services)
                      :conn
                      :uri)))
           true)

       "default protocol is http"
       props-aliased
       "http://localhost:9207"

       "uri should respect given protocol"
       (assoc props-aliased
              :protocol :https)
       "https://localhost:9207"

       "uri should respect given protocol, host and port"
       (assoc props-aliased
              :protocol :https
              :port 9201
              :host "cisco.com")
       "https://cisco.com:9201"))))

(deftest update-settings!-test
  (doseq [version [5 7]]
    (let [services (->ESConnServices)
          indexname "ctia_malware"
          initial-props {:entity :malware
                         :indexname indexname
                         :host "localhost"
                         :aliased true
                         :port (+ 9200 version)
                         :shards 5
                         :replicas 2
                         :refresh_interval "1s"
                         :version version}
          ;; create index
          {:keys [conn]} (sut/init-es-conn! initial-props services)
          new-props (assoc initial-props
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
        (is (= "2s" refresh_interval)))
      (index/delete! conn (str indexname "*")))))

(deftest get-existing-indices-test
  (for-each-es-version
   "get-existing-indices should retrieve existing indices if any."
   [5 7]
   #(index/delete! % (str indexname "*"))
   (let [successful? (atom true)
         fake-exit (fn [] (reset! successful? false))]
     (let [test-fn (fn [msg
                        input-indexname
                        expected-successful?
                        expected-output]
                     (assert (boolean? expected-successful?))
                     (reset! successful? true)
                     (testing msg
                       (let [ouput (with-redefs [sut/system-exit-error fake-exit]
                                     (sut/get-existing-indices conn input-indexname))]
                         ;; sut/system-exit-error is redefined to check if
                         ;; it was called but avoid to actually System/exit
                         ;; thus testing the output makes sense only on success
                         (when expected-successful?
                           (is (= expected-output ouput)))
                         (is (= expected-successful? @successful?)))))

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
                nil)))))

(deftest init-es-conn!-test
  (let [clean-template (fn [{:keys [uri] :as c}]
                         (http/delete (format "%s/_template/%s*" uri indexname)))
        clean-index #(index/delete! % (str indexname "*"))
        clean-all #(do (clean-index %) (clean-template %))
        prepare-props (fn [props version]
                        (assoc props
                               :version version
                               :port (+ 9200 version)))]
    (for-each-es-version
     "get-existing-indices should retrieve existing indices if any."
     [5 7]
     clean-index
     (testing "init-es-conn! should return a proper conn state with unaliased conf, but not create any index"
       (let [services (->ESConnServices)
             props (prepare-props props-not-aliased version)
             {:keys [index props config conn]}
             (sut/init-es-conn! props services)
             existing-index (index/get conn (str indexname "*"))]
         (is (empty? existing-index))
         (is (= index indexname))
         (is (= (:write-index props) indexname))
         (is (= (str "http://localhost:920" version) (:uri conn)))
         (is (nil? (:aliases config)))
         (is (= "1s" (get-in config [:settings :refresh_interval])))
         (is (= 1 (get-in config [:settings :number_of_replicas])))
         (is (= 2 (get-in config [:settings :number_of_shards])))
         (is (= {} (select-keys (:mappings config) [:a :b])))
         (clean-all conn)))

     (testing "update mapping should allow adding fields or identical mapping"
       (let [services (->ESConnServices)
             sucessful? (atom true)
             fake-exit (fn [] (reset! sucessful? false))
             props (prepare-props props-aliased version)
             test-fn (fn [msg expected-successful? field field-mapping]
                       ;; init and create aliased indices
                       (sut/init-es-conn! props services)
                       (with-redefs [sut/system-exit-error fake-exit
                                     ;; redef mappings
                                     sut/store-mappings
                                     (cond-> sut/store-mappings
                                       field (assoc-in [:sighting "sighting" :properties field]
                                                       field-mapping))]
                         (testing msg
                           ;; init again to trigger mapping update
                           (sut/init-es-conn! props services)
                           ;; check state
                           (is (= expected-successful? @sucessful?)))
                         ;; reset state
                         (clean-all conn)
                         (reset! sucessful? true)))]
       (test-fn "update mapping should not fail on unchanged mapping"
                true nil nil)
       (test-fn "update mapping should not fail on field addition"
                true :new-field m/token)
       (test-fn "Update mapping fails when modifying existing field mapping and CTIA must not start in that case."
                false :id m/text)))

     (testing "init-es-conn! should return a proper conn state with aliased conf, and create an initial aliased index"
       (let [services (->ESConnServices)
             {:keys [index props config conn]}
             (-> (prepare-props props-aliased version)
                 (sut/init-es-conn! services))
             existing-index (index/get conn (str indexname "*"))
             created-aliases (->> existing-index
                                  vals
                                  first
                                  :aliases
                                  keys
                                  set)]
         (is (= #{(keyword indexname) (keyword write-alias)}
                created-aliases))
         (is (= index indexname))
         (is (= (:write-index props) write-alias))
         (is (= (str "http://localhost:920" version) (:uri conn)))
         (is (= indexname
                (-> config :aliases keys first)))
         (is (= "2s" (get-in config [:settings :refresh_interval])))
         (is (= 1 (get-in config [:settings :number_of_replicas])))
         (is (= 2 (get-in config [:settings :number_of_shards])))
         (is (= {} (select-keys (:mappings config) [:a :b])))
         (clean-all conn)))

     (testing "init-es-conn! should return a conn state that ignore aliased conf setting when an unaliased index already exists"
       (index/create! conn
                      indexname
                      {:settings m/store-settings})
       (let [services (->ESConnServices)
             {:keys [index props config conn]}
             (-> (prepare-props props-aliased version)
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
         (is (= {} (select-keys (:mappings config) [:a :b]))))))))

(deftest update-index-state-test
  (let [test-fn (fn [{:keys [update-mappings update-settings]
                      :as props}]
                  (let [updated-mappings (atom false)
                        updated-template (atom false)
                        updated-settings (atom false)]
                    (with-redefs [sut/update-mappings! (fn [c] (reset! updated-mappings true))
                                  sut/update-settings! (fn [c] (reset! updated-settings true))
                                  sut/upsert-template! (fn [c] (reset! updated-template true))]
                      (sut/update-index-state {:props props})
                      (is (= @updated-mappings (boolean update-mappings)))
                      (is (= @updated-template (boolean update-mappings)))
                      (is (= @updated-settings (boolean update-settings))))))]
    (doseq [update-mappings? [true false nil]
            update-settings? [true false nil]]
      (test-fn (assoc props-aliased
                      :update-mappings update-mappings?
                      :update-settings update-settings?)))))
