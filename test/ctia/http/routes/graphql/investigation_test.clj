(ns ctia.http.routes.graphql.investigation-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.entity.investigation :as inv]
            [ctia.entity.investigation.examples :refer [investigation-maximal]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.fixtures :as fixt]
            [ctia.test-helpers.graphql :as gh]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(def ownership-data-fixture
  {:owner "foouser"
   :groups ["foogroup"]})

(def investigation-1 (fixt/randomize investigation-maximal))
(def investigation-2 (assoc
                      (fixt/randomize investigation-maximal)
                      :source "ngfw"))

(defn prepare-result
  [investigation]
  (dissoc investigation
          :search-txt))

(deftest investigation-graphql-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [inv1 (prepare-result
                 (gh/create-object "investigation" investigation-1))
           inv2 (prepare-result
                 (gh/create-object "investigation" investigation-2))
           graphql-queries (slurp "test/data/investigation.graphql")]

       (testing "investigation query"
         (let [{:keys [data errors status]}
               (gh/query graphql-queries
                         {:id (:id inv1)}
                         "InvestigationQueryTest")]

           (is (= 200 status))
           (is (empty? errors) "No errors")

           (testing "the investigation"
             (is (= inv1
                    (:investigation data))))))
       (testing "investigations query"
         (testing "investigations connection"
           (gh/connection-test "InvestigationsQueryTest"
                               graphql-queries
                               {"query" "*"}
                               [:investigations]
                               (map #(merge % ownership-data-fixture)
                                    [inv1 inv2]))

           (testing "sorting"
             (gh/connection-sort-test
              "InvestigationsQueryTest"
              graphql-queries
              {:query "*"}
              [:investigations]
              inv/investigation-fields)))
         (testing "query argument"
           (let [{:keys [data errors status]}
                 (gh/query graphql-queries
                           {:query "source:\"ngfw\""}
                           "InvestigationsQueryTest")]
             (is (= 200 status))
             (is (empty? errors) "No errors")
             (is (= 1 (get-in data [:investigations :totalCount]))
                 "Only one investigation matches to the query")
             (is (= [inv2]
                    (get-in data [:investigations :nodes]))
                 "The investigation matches the search query"))))))))
