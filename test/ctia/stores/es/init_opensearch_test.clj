(ns ctia.stores.es.init-opensearch-test
  "Tests for OpenSearch-specific behavior in init.clj"
  (:require [clojure.test :refer [deftest testing is]]
            [ctia.stores.es.init :as sut]
            [ctia.test-helpers.es :as es-helpers
             :refer [->ESConnServices]]
            [ductile.conn :as conn])
  (:import [java.util UUID]))

(deftest get-store-properties-engine-conversion-test
  (testing "get-store-properties should convert :engine from string to keyword"
    (let [services (->ESConnServices)
          ;; Simulate properties system reading engine as string
          get-in-config (fn [path default-val]
                          (if (= path [:ctia :store :es :default])
                            {:host "localhost"
                             :port 9202
                             :version 2
                             :engine "opensearch"}  ;; String, not keyword
                            default-val))
          props (sut/get-store-properties :test-store get-in-config)]
      (is (= :opensearch (:engine props))
          "Engine should be converted from string to keyword")
      (is (keyword? (:engine props))
          "Engine should be a keyword, not a string"))))

(deftest mk-index-ilm-config-opensearch-test
  (testing "mk-index-ilm-config should NOT add ILM lifecycle settings for OpenSearch"
    (let [services (->ESConnServices)
          indexname (str "test_opensearch_" (UUID/randomUUID))
          ;; Create a connection with OpenSearch engine
          opensearch-conn (conn/connect {:host "localhost"
                                         :port 9202
                                         :version 2
                                         :engine :opensearch
                                         :auth {:type :basic-auth
                                                :params {:user "admin" :pwd "admin"}}})
          store-config {:index indexname
                        :props {:write-index (str indexname "-write")}
                        :config {:settings {:refresh_interval "1s"}
                                 :mappings {}
                                 :aliases {}}
                        :conn opensearch-conn}
          result (sut/mk-index-ilm-config store-config)]
      (try
        ;; Verify lifecycle settings are NOT in the base config settings
        (let [settings (get-in result [:config :settings])]
          (is (nil? (get-in settings [:index :lifecycle]))
              "OpenSearch config should NOT contain ILM lifecycle settings"))

        ;; Verify lifecycle settings are NOT in the template settings
        (let [template-settings (get-in result [:config :template :template :settings])]
          (is (nil? (get-in template-settings [:index :lifecycle]))
              "OpenSearch template should NOT contain ILM lifecycle settings"))
        (finally
          (conn/close opensearch-conn)))))

  (testing "mk-index-ilm-config SHOULD add ILM lifecycle settings for Elasticsearch"
    (let [services (->ESConnServices)
          indexname (str "test_elasticsearch_" (UUID/randomUUID))
          ;; Create a connection with Elasticsearch engine
          es-conn (conn/connect {:host "localhost"
                                 :port 9207
                                 :version 7
                                 :engine :elasticsearch
                                 :auth {:type :basic-auth
                                        :params {:user "elastic" :pwd "ductile"}}})
          store-config {:index indexname
                        :props {:write-index (str indexname "-write")}
                        :config {:settings {:refresh_interval "1s"}
                                 :mappings {}
                                 :aliases {}}
                        :conn es-conn}
          result (sut/mk-index-ilm-config store-config)]
      (try
        ;; Verify lifecycle settings ARE in the base config settings for Elasticsearch
        (let [settings (get-in result [:config :settings])]
          (is (some? (get-in settings [:index :lifecycle]))
              "Elasticsearch config SHOULD contain ILM lifecycle settings")
          (is (= indexname (get-in settings [:index :lifecycle :name]))
              "Lifecycle name should match index name")
          (is (= (str indexname "-write") (get-in settings [:index :lifecycle :rollover_alias]))
              "Lifecycle rollover_alias should match write index"))
        (finally
          (conn/close es-conn))))))
