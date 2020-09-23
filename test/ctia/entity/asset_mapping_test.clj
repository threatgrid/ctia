(ns ctia.entity.asset-mapping-test
  (:require [clj-momo.lib.clj-time.coerce :as tc]
            [clj-momo.lib.clj-time.core :as t]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is are join-fixtures testing use-fixtures]]
            [ctia.entity.asset-mapping :as asset-mapping]
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
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn additional-tests [{:keys [short-id]} asset-mapping-sample]
  (testing "GET /ctia/asset-mapping/search"
   (let [app (helpers/get-current-app)
         {:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
    ;; only when ES store
    (when (= "es" (get-in-config [:ctia :store :asset-mapping]))
      (are [term check-fn expected desc] (let [response (helpers/get
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
        "Searching Asset Mapping by asset-ref"))))

  (testing "Expire valid-time"
    (let [fixed-now (-> "2020-12-31" tc/from-string tc/to-date)]
      (helpers/fixture-with-fixed-time
       fixed-now
       (fn []
         (let [response (helpers/post
                         (str "ctia/asset-mapping/expire/" short-id)
                         :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 200 (:status response)) "PATCH asset-mapping/expire succeeds")
           (is (= fixed-now (-> response :parsed-body :valid_time :end_time))
               ":valid_time properly reset")))))))

(deftest asset-mapping-routes-test
  (store/test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response http/api-key "foouser" "foogroup" "user")
     (entity-crud-test
      {:entity             "asset-mapping"
       :example            new-asset-mapping-maximal
       :invalid-tests?     true
       :invalid-test-field :asset_ref
       :update-tests?      true
       :search-tests?      true
       :search-field       :confidence
       :search-value       "High"
       :update-field       :source
       :additional-tests   additional-tests
       :headers            {:Authorization "45c1f5e3f05d0"}}))))

(deftest asset-mapping-pagination-test
  (store/test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [ids (helpers/post-entity-bulk
                new-asset-mapping-maximal
                :asset_mappings
                30
                {"Authorization" "45c1f5e3f05d0"})]

       (field-selection/field-selection-tests
        ["ctia/asset-mapping/search?query=*"
         (http/doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        asset-mapping/asset-mapping-fields)

       (pagination/pagination-test
        "ctia/asset-mapping/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        asset-mapping/asset-mapping-fields)))))

(deftest asset-mapping-metric-routes-test
  (aggregate/test-metric-routes
   (into asset-mapping/asset-mapping-entity
         {:plural            :asset_mappings
          :entity-minimal    new-asset-mapping-minimal
          :enumerable-fields asset-mapping/asset-mapping-enumerable-fields
          :date-fields       asset-mapping/asset-mapping-histogram-fields})))
