(ns ctia.entity.asset-mapping-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is are join-fixtures testing use-fixtures]]
            [ctia.entity.asset-mapping :as sut]
            [ctia.test-helpers.aggregate :as aggregate]
            [ctia.test-helpers.auth :as auth]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.field-selection :as field-selection]
            [ctia.test-helpers.http :as http]
            [ctia.test-helpers.pagination :as pagination]
            [ctia.test-helpers.store :as store]
            [ctim.examples.asset-mappings :refer [new-asset-mapping-minimal
                                                  new-asset-mapping-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(defn additional-tests [app {:keys [short-id]} asset-mapping-sample]
  (testing "GET /ctia/asset-mapping/search"
   (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
    ;; only when ES store
    (when (= "es" (get-in-config [:ctia :store :asset-mapping]))
      (are [term check-fn expected desc] (let [response (helpers/GET
                                                         app
                                                         "ctia/asset-mapping/search"
                                                         :query-params {"query" term}
                                                         :headers {"Authorization" "45c1f5e3f05d0"})]
                                           (is (= 200 (:status response)))
                                           (is (= expected (check-fn response)) desc)

                                           ;; to prevent `are` from double-failing
                                           true)

        "specificity:\"unique\""
        #(-> % :parsed-body first :specificity)
        (:specificity asset-mapping-sample)
        "Searching Asset Mapping by specificity"

        (str "asset_ref:\"" (:asset-ref asset-mapping-sample) "\"")
        #(-> % :parsed-body first :asset-ref)
        (:asset-ref asset-mapping-sample)
        "Searching Asset Mapping by asset-ref")))))

(deftest asset-mapping-routes-test
  (store/test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response app http/api-key "foouser" "foogroup" "user")
     (entity-crud-test
      (into sut/asset-mapping-entity
            {:app                app
             :example            new-asset-mapping-maximal
             :invalid-tests?     true
             :invalid-test-field :asset_ref
             :update-tests?      true
             :search-tests?      true
             :search-field       :confidence
             :search-value       "High"
             :update-field       :source
             :additional-tests   additional-tests
             :revoke-tests?      sut/asset-mapping-can-revoke?
             :headers            {:Authorization "45c1f5e3f05d0"}})))))

(deftest asset-mapping-pagination-test
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
                new-asset-mapping-maximal
                :asset_mappings
                pagination/pagination-sample-size
                {"Authorization" "45c1f5e3f05d0"})]

       (field-selection/field-selection-tests
        app
        ["ctia/asset-mapping/search?query=*"
         (http/doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/asset-mapping-fields)

       (pagination/pagination-test
        app
        "ctia/asset-mapping/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sut/asset-mapping-fields)))))

(deftest asset-mapping-metric-routes-test
  (aggregate/test-metric-routes
   (into sut/asset-mapping-entity
         {:plural            :asset_mappings
          :entity-minimal    new-asset-mapping-minimal
          :enumerable-fields sut/asset-mapping-enumerable-fields
          :date-fields       sut/asset-mapping-histogram-fields})))
