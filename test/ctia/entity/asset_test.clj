(ns ctia.entity.asset-test
  (:require
   [clj-momo.test-helpers.core :as mth]
   [clojure.test :refer [deftest is are join-fixtures testing use-fixtures]]
   [ctia.entity.asset :as asset]
   [ctia.test-helpers.aggregate :as aggregate]
   [ctia.test-helpers.auth :as auth]
   [ctia.test-helpers.core :as helpers]
   [ctia.test-helpers.crud :refer [entity-crud-test]]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.http :as http]
   [ctia.test-helpers.store :as store]
   [ctim.examples.assets :refer [new-asset-minimal new-asset-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(deftest set-asset-ref-test
  (testing "with proper tempids, transient asset_ref gets resolved"
    (let [entity  {:asset_ref "transient:asset-1"
                   :id        "asset-mapping-91bf8d35-648a-4965-af2c-e1c3d48a14b2"}
          tempids {"transient:asset-1" "http://non-transient-asset-id"}]
      (is (= {:asset_ref "http://non-transient-asset-id"
              :id        "asset-mapping-91bf8d35-648a-4965-af2c-e1c3d48a14b2"}
             (asset/set-asset-ref entity tempids)))))
  (testing "with no asset_ref in tempids, it returns entity with :error"
    (let [entity {:asset_ref "transient:asset-1"
                  :id        "asset-mapping-91bf8d35-648a-4965-af2c-e1c3d48a14b2"}
          tempids {}]
     (is (contains? (asset/set-asset-ref entity tempids) :error))))
  (testing "if asset_ref is a non-transient ID, returns normal entity"
    (let [entity {:asset_ref "http://non-transient-asset-id"
                  :id        "asset-mapping-91bf8d35-648a-4965-af2c-e1c3d48a14b2"}]
      (is (= {:asset_ref "http://non-transient-asset-id",
              :id        "asset-mapping-91bf8d35-648a-4965-af2c-e1c3d48a14b2"}
             (asset/set-asset-ref entity {})))
      (is (= {:asset_ref "http://non-transient-asset-id",
              :id        "asset-mapping-91bf8d35-648a-4965-af2c-e1c3d48a14b2"}
             (asset/set-asset-ref
              entity
              {"http://non-transient-asset-id" "http://different-asset-id"}))
          "it shouldn't try to renew non-transient asset_ref, even when tempids has it"))))

(defn additional-tests [app asset-id asset-sample]
  (testing "GET /ctia/asset/search"
    (do
      (are [term check-fn expected desc] (let [response (helpers/GET app
                                                                     (str "ctia/asset/search")
                                                                     :query-params {"query" term}
                                                                     :headers {"Authorization" "45c1f5e3f05d0"})]
                                           (is (= 200 (:status response)))
                                           (is (= expected (check-fn response)) desc))

        "asset_type:\"device\""
        #(-> % :parsed-body first :asset_type)
        (-> asset-sample :asset_type)
        "Searching by an Asset type works"))))

(deftest asset-routes-test
  (store/test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response app http/api-key "foouser" "foogroup" "user")
     (entity-crud-test
      (into asset/asset-entity
            {:app              app
             :example          new-asset-maximal
             :invalid-tests?   true
             :update-tests?    true
             :search-tests?    true
             :update-field     :source
             :additional-tests additional-tests
             :headers          {:Authorization "45c1f5e3f05d0"}})))))

(deftest asset-metric-routes-test
  (aggregate/test-metric-routes
   (into asset/asset-entity
         {:plural            :assets
          :entity-minimal    new-asset-minimal
          :enumerable-fields asset/asset-enumerable-fields
          :date-fields       asset/asset-histogram-fields})))
