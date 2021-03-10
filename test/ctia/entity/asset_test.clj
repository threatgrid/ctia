(ns ctia.entity.asset-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is are join-fixtures testing use-fixtures]]
            [ctia.entity.asset :as sut]
            [ctia.test-helpers.aggregate :as aggregate]
            [ctia.test-helpers.auth :as auth]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.field-selection :as field-selection]
            [ctia.test-helpers.http :as http]
            [ctia.test-helpers.pagination :as pagination]
            [ctia.test-helpers.store :as store]
            [ctim.examples.assets :refer [new-asset-minimal new-asset-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

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
      (into sut/asset-entity
            {:app              app
             :example          new-asset-maximal
             :invalid-tests?   true
             :update-tests?    true
             :search-tests?    true
             :update-field     :source
             :additional-tests additional-tests
             :headers          {:Authorization "45c1f5e3f05d0"}})))))

(deftest asset-pagination-test
  (store/test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [ids (helpers/POST-entity-bulk
                app
                new-asset-maximal
                :assets
                pagination/pagination-sample-size
                {"Authorization" "45c1f5e3f05d0"})]

       (field-selection/field-selection-tests
        app
        ["ctia/asset/search?query=*"
         (http/doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/asset-fields)

       (pagination/pagination-test
        app
        "ctia/asset/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sut/asset-fields)))))

(deftest asset-metric-routes-test
  (aggregate/test-metric-routes
   (into sut/asset-entity
         {:plural            :assets
          :entity-minimal    new-asset-minimal
          :enumerable-fields sut/asset-enumerable-fields
          :date-fields       sut/asset-histogram-fields})))
