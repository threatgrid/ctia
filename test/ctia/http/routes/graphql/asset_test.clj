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
   [ctim.examples.asset-mappings :refer [asset-mapping-maximal]]
   [ctim.examples.asset-properties :refer [asset-properties-maximal]]
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

(defn- rand-btw
  "Generates random int between two numbers"
  [a n] (+ a (rand-int (- (inc n) a))))

(defn- create-random-objects
  "Creates specified number of GraphQL objects"
  [app num entity-example]
  (repeatedly
   num
   #(gh/create-object
     app
     (:type entity-example)
     (fixt/randomize entity-example))))

(defn- create-asset-relation-objects
  "For every record of given `assets` creates random number of entities that have
  `asset_ref` associated with each asset"
  [app assets example]
  (->> assets
       (mapcat
        (fn [{:keys [id]}]
          (create-random-objects
           app
           (rand-btw 1 3)
           (-> example
               (dissoc :id)
               (assoc :asset_ref id)))))
       (into [])))

(deftest asset-graphql-asset-refs-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
     (testing "When queried for an Asset, should also be able to retrieve associated AssetMappings and AssetProperties"
       ;; create a few Assets, then per each Asset create a few AssetMappings
       ;; and AssetProperties then check for every asset_ref in AssetMappings
       ;; and AssetProperties to be the correct AssetId
       (let [assets           (create-random-objects app (rand-btw 3 5) (dissoc asset-maximal :id))
             asset-mappings   (create-asset-relation-objects app assets asset-mapping-maximal)
             asset-properties (create-asset-relation-objects app assets asset-properties-maximal)
             graphql-queries  (slurp "test/data/asset.graphql")]
         (doseq [{:keys [id]} assets]
           (let [{:keys [data errors status]} (gh/query
                                               app
                                               graphql-queries
                                               {:id id}
                                               "AssetRefQueryTest")]
             (is (= 200 status))
             (is (empty? errors) "No errors")
             (is (= (->> data :asset :asset_mappings :totalCount)
                    (->> data :asset :asset_mappings :nodes count)))
             (is (->> data :asset :asset_mappings :nodes
                      (every? #(-> % :asset_ref (= id))))
                 "AssetMappings' asset_ref is the exact asset id")
             (is (= (->> data :asset :asset_properties :totalCount)
                    (->> data :asset :asset_properties :nodes count)))
             (is (->> data :asset :asset_properties :nodes
                      (every? #(-> % :asset_ref (= id))))
                 "AssetProperties' asset_ref is the exact asset id"))))))))
