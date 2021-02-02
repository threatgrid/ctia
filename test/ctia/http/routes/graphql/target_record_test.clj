(ns ctia.http.routes.graphql.target-record-test
  (:require
   [clj-momo.test-helpers.core :as mth]
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [ctia.entity.target-record :as target-record]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.test-helpers.core :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.fixtures :as fixt]
   [ctia.test-helpers.graphql :as gh]
   [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
   [ctim.examples.target-records :refer [target-record-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(def ownership-data-fixture
  {:owner "foouser"
   :groups ["foogroup"]})

(def target-record-1 (fixt/randomize target-record-maximal))
(def target-record-2 (assoc
                      (fixt/randomize target-record-maximal)
                      :source "ngfw"))

(defn prepare-result
  [target-record]
  (dissoc target-record :search-txt))

;; (require '[hashp.core])

(deftest target-record-graphql-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [target-record1  (prepare-result
                            (gh/create-object app "target-record" target-record-1))
           target-record2  (prepare-result
                            (gh/create-object app "target-record" target-record-2))
           graphql-queries (slurp "test/data/target_record.graphql")]

       (testing "target record query"
         (let [{:keys [data
                       errors
                       status]} (gh/query
                                 app graphql-queries
                                 {:id (:id target-record1)}
                                 "TargetRecordQueryTest")]

           (is (= 200 status))
           (is (empty? errors) "No errors")

           (testing "the target-record" (is (= target-record1 (:target_record data))))))
       (testing "Target-records query"
         (testing "target-records connection"
           (gh/connection-test
            app
            "TargetRecordsQueryTest"
            graphql-queries
            {"query" "*"}
            [:target_records]
            (map #(merge % ownership-data-fixture)
                 [target-record1 target-record2]))
           (testing "sorting"
             (gh/connection-sort-test
              app
              "TargetRecordsQueryTest"
              graphql-queries
              {:query "*"}
              [:target_records]
              target-record/target-record-fields)))
         (testing "query argument"
           (let [{:keys [data errors status]}
                 (gh/query app
                           graphql-queries
                           {:query "source:\"ngfw\""}
                           "TargetRecordsQueryTest")]
             (is (= 200 status))
             (is (empty? errors) "No errors")
             (is (= 1 (get-in data [:target_records :totalCount]))
                 "Only one target-record matches the query")
             (is (= [target-record2]
                    (get-in data [:target_records :nodes]))
                 "The target-record matches the search query"))))))))
