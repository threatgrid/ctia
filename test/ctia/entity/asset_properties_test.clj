(ns ctia.entity.asset-properties-test
  (:require [clj-momo.lib.clj-time.coerce :as tc]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is are join-fixtures testing use-fixtures]]
            [ctia.entity.asset-properties :as asset-properties]
            [ctia.test-helpers.aggregate :as aggregate]
            [ctia.test-helpers.auth :as auth]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.field-selection :as field-selection]
            [ctia.test-helpers.http :as http]
            [ctia.test-helpers.pagination :as pagination]
            [ctia.test-helpers.store :as store]
            [ctim.examples.asset-properties :refer [new-asset-properties-minimal
                                                    new-asset-properties-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn additional-tests [app {:keys [short-id]} asset-properties-sample]
  (testing "GET /ctia/asset-properties/search"
   (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
    ;; only when ES store
    (when (= "es" (get-in-config [:ctia :store :asset-properties]))
      (are [term check-fn expected desc] (let [response (helpers/GET
                                                         app
                                                         "ctia/asset-properties/search"
                                                         :query-params {"query" term}
                                                         :headers {"Authorization" "45c1f5e3f05d0"})]
                                           (is (= 200 (:status response)))
                                           (is (= expected (check-fn response)) desc)

                                           ;; to prevent `are` from double-failing
                                           true)

        (str "properties.name:\"" (-> asset-properties-sample :properties first :name) "\"")
        (fn [r] (-> r :parsed-body first :properties first :name))
        (-> asset-properties-sample :properties first :name)
        "Properties name match"

        (str "properties.value:\"" (-> asset-properties-sample :properties first :value) "\"")
        (fn [r] (-> r :parsed-body first :properties first :value))
        (-> asset-properties-sample :properties first :value)
        "Properties value match"

        (str "asset_ref:\"" (:asset-ref asset-properties-sample) "\"")
        #(-> % :parsed-body first :asset-ref)
        (:asset-ref asset-properties-sample)
        "Searching Asset Properties by asset-ref")))

  (testing "Expire 'valid-time' field"
    (let [fixed-now (-> "2020-12-31" tc/from-string tc/to-date)]
      (helpers/fixture-with-fixed-time
       fixed-now
       (fn []
         (let [response (helpers/POST
                         app
                         (str "ctia/asset-properties/expire/" short-id)
                         :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 200 (:status response)) "POST asset-properties/expire succeeds")
           (is (= fixed-now (-> response :parsed-body :valid_time :end_time))
               ":valid_time properly reset"))))))))

(deftest asset-properties-routes-test
  (store/test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response app http/api-key "foouser" "foogroup" "user")
     (entity-crud-test
      {:app                app
       :entity             "asset-properties"
       :example            new-asset-properties-maximal
       :invalid-tests?     true
       :invalid-test-field :asset_ref
       :update-tests?      true
       :update-field       :source
       :search-tests?      true
       :search-field       :source
       :search-value       "cisco:unified_connect"
       :additional-tests   additional-tests
       :headers            {:Authorization "45c1f5e3f05d0"}}))))

(deftest asset-properties-pagination-test
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
                new-asset-properties-maximal
                :asset_properties
                30
                {"Authorization" "45c1f5e3f05d0"})]

       (field-selection/field-selection-tests
        app
        ["ctia/asset-properties/search?query=*"
         (http/doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        asset-properties/asset-properties-fields)

       (pagination/pagination-test
        app
        "ctia/asset-properties/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        asset-properties/asset-properties-fields)))))

(deftest asset-properties-metric-routes-test
  (aggregate/test-metric-routes
   (into asset-properties/asset-properties-entity
         {:plural            :asset_properties
          :entity-minimal    new-asset-properties-minimal
          :enumerable-fields asset-properties/asset-properties-enumerable-fields
          :date-fields       asset-properties/asset-properties-histogram-fields})))
