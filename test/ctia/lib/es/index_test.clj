(ns ctia.lib.es.index-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.lib.es
             [conn :as es-conn]
             [index :as es-index]]
            [ctia.properties :refer [properties]]
            [ctia.test-helpers
             [core :as test-helpers]
             [es :as es-helpers]]))

(use-fixtures :once mth/fixture-schema-validation)
(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
                                    test-helpers/fixture-ctia]))

(deftest index-uri-test
  (testing "should generate a valid index URI"
    (is (= (es-index/index-uri "http://127.0.0.1" "test")
           "http://127.0.0.1/test"))))

(deftest template-uri-test
  (testing "should generate a valid template URI"
    (is (= (es-index/template-uri "http://127.0.0.1" "test")
           "http://127.0.0.1/_template/test"))))

(deftest index-crud-ops
  (testing "with ES conn test setup"

    (let [conn (es-conn/connect
                {:host (get-in @properties [:ctia :store :es :default :host])
                 :port (get-in @properties [:ctia :store :es :default :port])})]

      (testing "all ES Index CRUD operations"
        (let [index-create-res
              (es-index/create! conn "test_index"
                                {:settings {:number_of_shards 1
                                            :number_of_replicas 1}})
              index-get-res (es-index/get conn "test_index")
              index-delete-res (es-index/delete! conn "test_index")]

          (es-index/delete! conn "test_index")

          (is (true? (boolean index-create-res)))
          (is (= {:test_index
                  {:aliases {},
                   :mappings {},
                   :settings
                   {:index
                    {:number_of_shards "1"
                     :number_of_replicas "1"
                     :provided_name "test_index"}}}}

                 (update-in index-get-res
                            [:test_index :settings :index]
                            dissoc
                            :creation_date
                            :uuid
                            :version)))

          (is (true? (boolean index-delete-res))))))))
