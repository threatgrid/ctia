(ns ctia.entity.asset-properties-test
  (:require [clj-momo.test-helpers.core :as mth]
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

(defn additional-tests [_ asset-properties-sample]
  (testing "GET /ctia/asset-properties/search"
   (let [app (helpers/get-current-app)
         get-in-config (helpers/current-get-in-config-fn app)]
    ;; only when ES store
    (when (= "es" (get-in-config [:ctia :store :asset-properties]))
      (are [term check-fn expected desc] (let [response (helpers/get
                                                         "ctia/asset-properties/search"
                                                         :query-params {"query" term}
                                                         :headers {"Authorization" "45c1f5e3f05d0"})]
                                           (is (= 200 (:status response)))
                                           (is (= expected (check-fn response)) desc))

        "*"
        (fn [r] (-> r :parsed-body first :properties first :name))
        (-> asset-properties-sample :properties first :name)
        "Properties name match"

        "*"
        (fn [r] (-> r :parsed-body first :properties first :value))
        (-> asset-properties-sample :properties first :value)
        "Properties value match"

        (str "asset_ref:\"" (:asset-ref asset-properties-sample) "\"")
        #(-> % :parsed-body first :asset-ref)
        (:asset-ref asset-properties-sample)
        "Searching Asset Properties by asset-ref")))))

(deftest asset-properties-routes-test
  (store/test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response http/api-key "foouser" "foogroup" "user")
     (entity-crud-test
      {:entity             "asset-properties"
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
  (store/test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [ids (helpers/post-entity-bulk
                new-asset-properties-maximal
                :asset_properties
                30
                {"Authorization" "45c1f5e3f05d0"})]

       (field-selection/field-selection-tests
        ["ctia/asset-properties/search?query=*"
         (http/doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        asset-properties/asset-properties-fields)

       (pagination/pagination-test
        "ctia/asset-properties/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        asset-properties/asset-properties-fields)))))

(deftest asset-metric-routes-test
  ((:es-store store/store-fixtures)
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "Administrators" "user")
     (aggregate/test-metric-routes
      (into asset-properties/asset-properties-entity
            {:plural            :asset_properties
             :entity-minimal    new-asset-properties-minimal
             :enumerable-fields asset-properties/asset-properties-enumerable-fields
             :date-fields       asset-properties/asset-properties-histogram-fields})))))
