(ns ctia.http.routes.graphql.asset-test
  (:require
   [clj-momo.test-helpers.core :as mth]
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [ctia.entity.asset :as asset]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.test-helpers.core :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.fixtures :as fixt]
   [ctia.test-helpers.graphql :as gh]
   [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
   [ctim.examples.assets :refer [asset-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(def ownership-data-fixture
  {:owner "foouser"
   :groups ["foogroup"]})

(def asset-1 (fixt/randomize asset-maximal))
(def asset-2 (assoc
              (fixt/randomize asset-maximal)
              :source "ngfw"))

(defn prepare-result
  [asset]
  (dissoc asset :search-txt))

(deftest asset-graphql-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [asset1 (prepare-result
                   (gh/create-object app "asset" asset-1))
           asset2 (prepare-result
                   (gh/create-object app "asset" asset-2))
           graphql-queries (slurp "test/data/asset.graphql")]

       (testing "asset query"
         (let [{:keys [data
                       errors
                       status]} (gh/query
                                 app graphql-queries
                                 {:id (:id asset1)}
                                 "AssetQueryTest")]

           (is (= 200 status))
           (is (empty? errors) "No errors")

           (testing "the asset" (is (= asset1 (:asset data))))))
       (testing "assets query"
         (testing "assets connection"
           (gh/connection-test
            app
            "AssetsQueryTest"
            graphql-queries
            {"query" "*"}
            [:assets]
            (map #(merge % ownership-data-fixture)
                 [asset1 asset2]))
           (testing "sorting"
             (gh/connection-sort-test
              app
              "AssetsQueryTest"
              graphql-queries
              {:query "*"}
              [:assets]
              asset/asset-fields)))
         (testing "query argument"
           (let [{:keys [data errors status]}
                 (gh/query app
                           graphql-queries
                           {:query "source:\"ngfw\""}
                           "AssetsQueryTest")]
             (is (= 200 status))
             (is (empty? errors) "No errors")
             (is (= 1 (get-in data [:assets :totalCount]))
                 "Only one asset matches the query")
             (is (= [asset2]
                    (get-in data [:assets :nodes]))
                 "The asset matches the search query"))))))))
