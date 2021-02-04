(ns ctia.http.routes.graphql.asset-mapping-test
  (:require
   [clj-momo.test-helpers.core :as mth]
   [clojure.test :refer [deftest is are join-fixtures testing use-fixtures]]
   [ctia.entity.asset-mapping :as asset-mapping]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.test-helpers.core :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.fixtures :as fixt]
   [ctia.test-helpers.graphql :as gh]
   [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
   [ctim.examples.asset-mappings :refer [asset-mapping-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(def ownership-data-fixture
  {:owner "foouser"
   :groups ["foogroup"]})

(def asset-mapping-1 (fixt/randomize asset-mapping-maximal))
(def asset-mapping-2 (assoc
                      (fixt/randomize asset-mapping-maximal)
                      :source "ngfw"))

(defn prepare-result
  [asset-mapping]
  (dissoc asset-mapping :search-txt))

(deftest asset-mapping-graphql-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [asset-mapping1  (prepare-result
                            (gh/create-object app "asset-mapping" asset-mapping-1))
           asset-mapping2  (prepare-result
                            (gh/create-object app "asset-mapping" asset-mapping-2))
           graphql-queries (slurp "test/data/asset_mapping.graphql")]

       (testing "asset mapping query"
         (let [{:keys [data
                       errors
                       status]} (gh/query
                                 app graphql-queries
                                 {:id (:id asset-mapping1)}
                                 "AssetMappingQueryTest")]

           (is (= 200 status))
           (is (empty? errors) "No errors")

           (testing "the asset-mapping" (is (= asset-mapping1 (:asset_mapping data))))))
       (testing "asset-mappings query"
         (testing "asset-mappings connection"
           (gh/connection-test
            app
            "AssetMappingsQueryTest"
            graphql-queries
            {"query" "*"}
            [:asset_mappings]
            (map #(merge % ownership-data-fixture)
                 [asset-mapping1 asset-mapping2]))
           (testing "sorting"
             (gh/connection-sort-test
              app
              "AssetMappingsQueryTest"
              graphql-queries
              {:query "*"}
              [:asset_mappings]
              asset-mapping/asset-mapping-fields)))
         (testing "query argument"
           (are [query data-vec expected] (let [{:keys [data errors status]}
                                                (gh/query
                                                 app
                                                 graphql-queries
                                                 {:query query}
                                                 "AssetMappingsQueryTest")]
                                            (is (= 200 status))
                                            (is (empty? errors) "No errors")
                                            (is (= expected (get-in data data-vec))
                                                (format "make sure data at '%s' matches the expected" data-vec))
                                            true)
             "source:\"ngfw\"" [:asset_mappings :totalCount] 1
             "source:\"ngfw\"" [:asset_mappings :nodes] [asset-mapping2]

             (format "observable.type:\"%s\""
                     (-> asset-mapping2 :observable :type))
             [:asset_mappings :nodes 0 :observable :value]
             (-> asset-mapping2 :observable :value))))))))
