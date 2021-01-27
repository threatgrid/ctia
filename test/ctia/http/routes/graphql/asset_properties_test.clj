(ns ctia.http.routes.graphql.asset-properties-test
  (:require
   [clj-momo.test-helpers.core :as mth]
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [ctia.entity.asset-properties :as asset-properties]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.test-helpers.core :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.fixtures :as fixt]
   [ctia.test-helpers.graphql :as gh]
   [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
   [ctim.examples.asset-properties :refer [asset-properties-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(def ownership-data-fixture
  {:owner "foouser"
   :groups ["foogroup"]})

(def asset-properties-1 (fixt/randomize asset-properties-maximal))
(def asset-properties-2 (assoc
                      (fixt/randomize asset-properties-maximal)
                      :source "ngfw"))

(defn prepare-result
  [asset-properties]
  (dissoc asset-properties :search-txt))


(deftest asset-properties-graphql-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [asset-properties1 (prepare-result
                              (gh/create-object app "asset-properties" asset-properties-1))
           asset-properties2  (prepare-result
                               (gh/create-object app "asset-properties" asset-properties-2))
           graphql-queries (slurp "test/data/asset_properties.graphql")]

       (testing "asset properties query"
         (let [{:keys [data
                       errors
                       status]} (gh/query
                                 app graphql-queries
                                 {:id (:id asset-properties1)}
                                 "AssetPropertyQueryTest")]

           (is (= 200 status))
           (is (empty? errors) "No errors")

           (testing "the asset-properties" (is (= asset-properties1 (:asset_property data))))))
       (testing "asset-propertiess query"
         (testing "asset-propertiess connection"
           (gh/connection-test
            app
            "AssetPropertiesQueryTest"
            graphql-queries
            {"query" "*"}
            [:asset_properties]
            (map #(merge % ownership-data-fixture)
                 [asset-properties1 asset-properties2]))
           (testing "sorting"
             (gh/connection-sort-test
              app
              "AssetPropertiesQueryTest"
              graphql-queries
              {:query "*"}
              [:asset_properties]
              asset-properties/asset-properties-fields)))
         (testing "query argument"
           (let [{:keys [data errors status]}
                 (gh/query app
                           graphql-queries
                           {:query "source:\"ngfw\""}
                           "AssetPropertiesQueryTest")]
             (is (= 200 status))
             (is (empty? errors) "No errors")
             (is (= 1 (get-in data [:asset_properties :totalCount]))
                 "Only one asset-properties matches the query")
             (is (= [asset-properties2]
                    (get-in data [:asset_properties :nodes]))
                 "The asset-properties matches the search query"))))))))
