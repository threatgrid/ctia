(ns ctia.stores.es.opensearch-integration-test
  "Integration tests for OpenSearch 2.x and 3.x support.
   These tests verify that CTIA works correctly with OpenSearch,
   including automatic ILM→ISM policy transformation."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as string]
            [ctia.stores.es.init :as init]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :as es-helpers
             :refer [->ESConnServices]]
            [ductile.conn :as conn]
            [ductile.index :as index])
  (:import [java.util UUID]))

;; Test fixtures for OpenSearch 2
(use-fixtures :once es-helpers/fixture-properties:opensearch-store)

(defn gen-indexname []
  (str "ctia_opensearch_test_" (UUID/randomUUID)))

(def opensearch-auth
  {:type :basic-auth
   :params {:user "admin" :pwd "admin"}})

(defn mk-opensearch-props
  "Create properties for OpenSearch connection"
  [indexname & {:keys [version port engine]
                :or {version 2
                     port 9202
                     engine :opensearch}}]
  {:entity :sighting
   :indexname indexname
   :refresh_interval "1s"
   :shards 1
   :replicas 1
   :host "localhost"
   :port port
   :version version
   :engine engine
   :rollover {:max_docs 100}
   :auth opensearch-auth
   :update-mappings true
   :update-settings true
   :refresh-mappings true})

(deftest opensearch-connection-test
  (testing "OpenSearch 2: Should establish connection successfully"
    (let [services (->ESConnServices)
          indexname (gen-indexname)
          props (mk-opensearch-props indexname)
          {:keys [conn index]} (init/init-store-conn props services)]
      (try
        (is (some? conn) "Connection should be established")
        (is (= :opensearch (:engine conn)) "Engine should be :opensearch")
        (is (= 2 (:version conn)) "Version should be 2")
        (is (= "http://localhost:9202" (:uri conn)) "URI should point to OpenSearch 2")
        (is (= indexname index) "Index name should match")
        (finally
          (es-helpers/clean-es-state! conn (str indexname "*"))
          (conn/close conn)))))

  (testing "OpenSearch 3: Should establish connection successfully"
    (let [services (->ESConnServices)
          indexname (gen-indexname)
          props (mk-opensearch-props indexname :version 3 :port 9203)
          {:keys [conn index]} (init/init-store-conn props services)]
      (try
        (is (some? conn) "Connection should be established")
        (is (= :opensearch (:engine conn)) "Engine should be :opensearch")
        (is (= 3 (:version conn)) "Version should be 3")
        (is (= "http://localhost:9203" (:uri conn)) "URI should point to OpenSearch 3")
        (is (= indexname index) "Index name should match")
        (finally
          (es-helpers/clean-es-state! conn (str indexname "*"))
          (conn/close conn))))))

(deftest opensearch-index-creation-test
  (testing "OpenSearch 2: Should create indices with proper configuration"
    (let [services (->ESConnServices)
          indexname (gen-indexname)
          props (mk-opensearch-props indexname)
          {:keys [conn index config]} (init/init-store-conn props services)]
      (try
        ;; Create the index
        (init/init-es-conn! props services)

        ;; Verify index exists
        (let [indices (keys (index/get conn (str indexname "*")))]
          (is (seq indices) "Indices should be created")
          (is (some #(string/includes? (name %) indexname) indices)
              "Created index should contain the indexname"))

        ;; Verify settings
        (let [index-info (first (vals (index/get conn (str indexname "*"))))
              settings (get-in index-info [:settings :index])]
          (is (= "1" (:number_of_shards settings)) "Shards should match configuration")
          (is (= "1" (:number_of_replicas settings)) "Replicas should match configuration")
          (is (= "1s" (:refresh_interval settings)) "Refresh interval should match configuration"))
        (finally
          (es-helpers/clean-es-state! conn (str indexname "*"))
          (conn/close conn))))))

(deftest opensearch-policy-transformation-test
  (testing "OpenSearch 2: ILM policy should be transformed to ISM format"
    (let [services (->ESConnServices)
          indexname (gen-indexname)
          props (mk-opensearch-props indexname)
          {:keys [conn]} (init/init-store-conn props services)]
      (try
        ;; Initialize store with ILM policy
        (init/init-es-conn! props services)

        ;; Get the policy (should be ISM format for OpenSearch)
        (let [policy (index/get-policy conn indexname)]
          (is (some? policy) "Policy should be created")
          ;; OpenSearch uses ISM format with "states", not ILM "phases"
          (is (or (contains? (get-in policy [(keyword indexname) :policy]) :states)
                  (contains? (get-in policy [(keyword indexname) :policy]) :phases))
              "Policy should be in ISM or ILM format"))
        (finally
          (es-helpers/clean-es-state! conn (str indexname "*"))
          (conn/close conn)))))

  (testing "OpenSearch 3: ILM policy should be transformed to ISM format"
    (let [services (->ESConnServices)
          indexname (gen-indexname)
          props (mk-opensearch-props indexname :version 3 :port 9203)
          {:keys [conn]} (init/init-store-conn props services)]
      (try
        ;; Initialize store with ILM policy
        (init/init-es-conn! props services)

        ;; Get the policy (should be ISM format for OpenSearch)
        (let [policy (index/get-policy conn indexname)]
          (is (some? policy) "Policy should be created")
          ;; OpenSearch uses ISM format with "states", not ILM "phases"
          (is (or (contains? (get-in policy [(keyword indexname) :policy]) :states)
                  (contains? (get-in policy [(keyword indexname) :policy]) :phases))
              "Policy should be in ISM or ILM format"))
        (finally
          (es-helpers/clean-es-state! conn (str indexname "*"))
          (conn/close conn))))))

(deftest opensearch-settings-update-test
  (testing "OpenSearch 2: Dynamic settings should be updatable"
    (let [services (->ESConnServices)
          indexname (gen-indexname)
          initial-props (mk-opensearch-props indexname)
          {:keys [conn]} (init/init-es-conn! initial-props services)]
      (try
        ;; Update settings
        (let [new-props (assoc initial-props
                               :replicas 2
                               :refresh_interval "5s")]
          (init/update-settings! (init/init-store-conn new-props services))

          ;; Verify updated settings
          (let [index-info (first (vals (index/get conn (str indexname "*"))))
                settings (get-in index-info [:settings :index])]
            (is (= "1" (:number_of_shards settings))
                "Shards should remain unchanged (static parameter)")
            (is (= "2" (:number_of_replicas settings))
                "Replicas should be updated")
            (is (= "5s" (:refresh_interval settings))
                "Refresh interval should be updated")))
        (finally
          (es-helpers/clean-es-state! conn (str indexname "*"))
          (conn/close conn))))))

(deftest opensearch-index-template-test
  (testing "OpenSearch 2: Index templates should be created without ILM lifecycle settings"
    (let [services (->ESConnServices)
          indexname (gen-indexname)
          props (mk-opensearch-props indexname)
          {:keys [conn]} (init/init-store-conn props services)]
      (try
        ;; Initialize store
        (init/init-es-conn! props services)

        ;; Verify index template exists
        (let [template (index/get-index-template conn indexname)]
          (is (some? template) "Index template should be created")
          ;; Verify that lifecycle settings are NOT in the template (OpenSearch doesn't support them)
          (let [template-settings (get-in template [(keyword indexname) :template :settings :index])]
            (is (nil? (:lifecycle template-settings))
                "OpenSearch templates should not contain ILM lifecycle settings")))
        (finally
          (es-helpers/clean-es-state! conn (str indexname "*"))
          (conn/close conn))))))

(deftest opensearch-aliases-test
  (testing "OpenSearch 2: Aliases should be created correctly"
    (let [services (->ESConnServices)
          indexname (gen-indexname)
          props (mk-opensearch-props indexname)
          state (init/init-es-conn! props services)
          {:keys [conn props]} state
          write-index (:write-index props)]
      (try
        ;; Verify aliases
        (let [indices (index/get conn (str indexname "*"))
              aliases-found (set (mapcat (fn [[_ info]] (keys (:aliases info))) indices))]
          (is (contains? aliases-found (keyword indexname))
              "Read alias should exist")
          (is (contains? aliases-found (keyword write-index))
              "Write alias should exist"))
        (finally
          (es-helpers/clean-es-state! conn (str indexname "*"))
          (conn/close conn))))))
