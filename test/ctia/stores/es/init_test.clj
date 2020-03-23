(ns ctia.stores.es.init-test
  (:require [ctia.stores.es.init :as sut]
            [clj-http.client :as http]
            [ctia.stores.es.mapping :as m]
            [clj-momo.lib.es
             [index :as index]
             [conn :as conn]]
            [clojure.test :refer [deftest testing is]]))

(def es-conn (conn/connect {:host "localhost"
                            :port "9200"}))
(def indexname "ctia_init_test_sighting")
(def write-alias (str indexname "-write"))
(def props-aliased {:entity :sighting
                    :indexname indexname
                    :refresh_interval "2s"
                    :shards 2
                    :replicas 1
                    :mappings {:a 1 :b 2}
                    :host "localhost"
                    :port 9200
                    :aliased true})

(def props-not-aliased {:entity :sighting
                        :indexname indexname
                        :shards 2
                        :replicas 1
                        :mappings {:a 1 :b 2}
                        :host "localhost"
                        :port 9200
                        :aliased false})

(deftest init-store-conn-test
  (testing "init store conn should return a proper conn state with unaliased conf"
    (let [{:keys [index props config conn]}
          (sut/init-store-conn props-not-aliased)]
      (is (= index indexname))
      (is (= (:write-index props) indexname))
      (is (= "http://localhost:9200" (:uri conn)))
      (is (nil? (:aliases config)))
      (is (= "1s" (get-in config [:settings :refresh_interval])))
      (is (= 1 (get-in config [:settings :number_of_replicas])))
      (is (= 2 (get-in config [:settings :number_of_shards])))
      (is (= {} (select-keys (:mappings config) [:a :b])))))

  (testing "init store conn should return a proper conn state with aliased conf"
    (let [{:keys [index props config conn]}
          (sut/init-store-conn props-aliased)]
      (is (= index indexname))
      (is (= (:write-index props) write-alias))
      (is (= "http://localhost:9200" (:uri conn)))
      (is (= indexname
             (-> config :aliases keys first)))
      (is (= "2s" (get-in config [:settings :refresh_interval])))
      (is (= 1 (get-in config [:settings :number_of_replicas])))
      (is (= 2 (get-in config [:settings :number_of_shards])))
      (is (= {} (select-keys (:mappings config) [:a :b]))))))

(deftest update-settings!-test
  (let [indexname "ctia_malware"
        initial-props {:entity :malware
                       :indexname indexname
                       :host "localhost"
                       :aliased true
                       :port 9200
                       :shards 5
                       :replicas 2
                       :refresh_interval "1s"}
        ;; create index
        {:keys [conn]} (sut/init-es-conn! initial-props)
        new-props (assoc initial-props
                         :shards 4
                         :replicas 1
                         :refresh_interval "2s")
        _ (sut/update-settings! (sut/init-store-conn new-props))
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
    (index/delete! conn (str indexname "*"))))

(deftest init-es-conn!-test
  (index/delete! es-conn (str indexname "*"))
  (testing "init-es-conn! should return a proper conn state with unaliased conf, but not create any index"
    (let [{:keys [index props config conn]}
          (sut/init-es-conn! props-not-aliased)
          existing-index (index/get es-conn (str indexname "*"))]
      (is (empty? existing-index))
      (is (= index indexname))
      (is (= (:write-index props) indexname))
      (is (= "http://localhost:9200" (:uri conn)))
      (is (nil? (:aliases config)))
      (is (= "1s" (get-in config [:settings :refresh_interval])))
      (is (= 1 (get-in config [:settings :number_of_replicas])))
      (is (= 2 (get-in config [:settings :number_of_shards])))
      (is (= {} (select-keys (:mappings config) [:a :b])))))

  (testing "update mapping should allow adding fields or identical mapping"
    (let [exited (atom false)
          fake-exit (fn [] (reset! exited true))
          test-fn (fn [msg error? field field-mapping]
                    ;; init and create aliased indices
                    (sut/init-es-conn! props-aliased)
                    (with-redefs [sut/system-exit-error fake-exit
                                  ;; redef mappings
                                  sut/store-mappings
                                  (cond-> sut/store-mappings
                                    field (assoc-in [:sighting "sighting" :properties field]
                                                    field-mapping))]
                      ;; init again to trigger mapping update
                      (sut/init-es-conn! props-aliased)
                      ; check state
                      (is (= error? @exited) msg)
                      ;; reset state
                      (index/delete! es-conn (str indexname "*"))
                      (reset! exited false)))]
      (test-fn "update mapping should not fail on unchanged mapping"
               false nil nil)
      (test-fn "update mapping should not fail on field addition"
               false :new-field m/token)
      (test-fn "update mapping should fail when modifying existing field mapping"
               true :id m/text)))

  (testing "init-es-conn! should return a proper conn state with aliased conf, and create an initial aliased index"
    (index/delete! es-conn (str indexname "*"))
    (let [{:keys [index props config conn]}
          (sut/init-es-conn! props-aliased)
          existing-index (index/get es-conn (str indexname "*"))
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
      (is (= "http://localhost:9200" (:uri conn)))
      (is (= indexname
             (-> config :aliases keys first)))
      (is (= "2s" (get-in config [:settings :refresh_interval])))
      (is (= 1 (get-in config [:settings :number_of_replicas])))
      (is (= 2 (get-in config [:settings :number_of_shards])))
      (is (= {} (select-keys (:mappings config) [:a :b])))))

  (testing "init-es-conn! should return a conn state that ignore aliased conf setting when an unaliased index already exists"
    (index/delete! es-conn (str indexname "*"))
    (http/delete (str "http://localhost:9200/_template/" indexname "*"))
    (index/create! es-conn
                   indexname
                   {:settings m/store-settings})
    (let [{:keys [index props config conn]}
          (sut/init-es-conn! props-aliased)
          existing-index (index/get es-conn (str indexname "*"))
          created-aliases (->> existing-index
                               vals
                               first
                               :aliases
                               keys
                               set)]
      (is (= #{} created-aliases))
      (is (= index indexname))
      (is (= (:write-index props) indexname))
      (is (= "http://localhost:9200" (:uri conn)))
      (is (= indexname
             (-> config :aliases keys first)))
      (is (= 1 (get-in config [:settings :number_of_replicas])))
      (is (= 2 (get-in config [:settings :number_of_shards])))
      (is (= {} (select-keys (:mappings config) [:a :b])))))

  (http/delete (str "http://localhost:9200/_template/" indexname "*"))
  (index/delete! es-conn (str indexname "*")))
