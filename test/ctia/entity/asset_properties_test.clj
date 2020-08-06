(ns ctia.entity.asset-properties-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is are join-fixtures testing use-fixtures]]
            [ctia.entity.asset-properties :as asset-properties]
            [ctia.properties :as p]
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

;; (helpers/get
;;  "ctia/asset-properties/search"
;;  :query-params {"query" "asset_ref:\"http://ex.tld/ctia/asset/asset-61884b14-e273-4930-a5ff-dcce69207724\""}
;;  :headers {"Authorization" "45c1f5e3f05d0"})

(defn additional-tests [_ asset-properties-sample]
  (testing "GET /ctia/asset-properties/search"
    ;; only when ES store
    (when (= "es" (p/get-in-global-properties [:ctia :store :asset-properties]))
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

        ))))

(deftest asset-properties-routes-test
  (store/test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response http/api-key "foouser" "foogroup" "user")
     (entity-crud-test
      {:entity           "asset-properties"
       :example          new-asset-properties-maximal
       :invalid-tests?   true
       :update-tests?    true
       :search-tests?    true
       :search-field     :source
       :search-value     "cisco:unified_connect"
       :update-field     :source
       :additional-tests additional-tests
       :headers          {:Authorization "45c1f5e3f05d0"}}))))

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

#_(deftest asset-metric-routes-test
  ((:es-store store/store-fixtures)
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "Administrators" "user")
     (aggregate/test-metric-routes
      (into asset-mapping/asset-mapping-entity
            {:plural            :assets
             :entity-minimal    new-asset-mapping-minimal
             :enumerable-fields asset-mapping/asset-mapping-enumerable-fields
             :date-fields       asset-mapping/asset-mapping-histogram-fields})))))
