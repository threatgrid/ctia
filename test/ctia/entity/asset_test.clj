(ns ctia.entity.asset-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is are join-fixtures testing use-fixtures]]
            [ctia.entity.asset :as asset]
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
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn additional-tests [asset-id asset-sample]
  (testing "GET /ctia/asset/search"
   (let [app (helpers/get-current-app)
         {:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
    ;; only when ES store
    (when (= "es" (get-in-config [:ctia :store :asset]))
      (are [term check-fn expected desc] (let [response (helpers/get (str "ctia/asset/search")
                                                                     :query-params {"query" term}
                                                                     :headers {"Authorization" "45c1f5e3f05d0"})]
                                           (is (= 200 (:status response)))
                                           (is (= expected (check-fn response)) desc))

        "asset_type:\"device\""
        #(-> % :parsed-body first :asset_type)
        (-> asset-sample :asset_type)
        "Searching by an Asset type works")))))

(deftest asset-routes-test
  (store/test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response http/api-key "foouser" "foogroup" "user")
     (entity-crud-test
      {:entity           "asset"
       :example          new-asset-maximal
       :invalid-tests?   true
       :update-tests?    true
       :search-tests?    true
       :update-field     :source
       :additional-tests additional-tests
       :headers          {:Authorization "45c1f5e3f05d0"}}))))

(deftest asset-pagination-test
  (store/test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [ids (helpers/post-entity-bulk
                new-asset-maximal
                :assets
                30
                {"Authorization" "45c1f5e3f05d0"})]

       (field-selection/field-selection-tests
        ["ctia/asset/search?query=*"
         (http/doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        asset/asset-fields)

       (pagination/pagination-test
        "ctia/asset/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        asset/asset-fields)))))

(deftest asset-metric-routes-test
  ((:es-store store/store-fixtures)
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "Administrators" "user")
     (aggregate/test-metric-routes
      (into asset/asset-entity
            {:plural            :assets
             :entity-minimal    new-asset-minimal
             :enumerable-fields asset/asset-enumerable-fields
             :date-fields       asset/asset-histogram-fields})))))
