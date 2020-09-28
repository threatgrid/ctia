(ns ctia.entity.target-record-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is are join-fixtures testing use-fixtures]]
            [ctia.entity.target-record :as target-record]
            [ctia.test-helpers.aggregate :as aggregate]
            [ctia.test-helpers.auth :as auth]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.field-selection :as field-selection]
            [ctia.test-helpers.http :as http]
            [ctia.test-helpers.pagination :as pagination]
            [ctia.test-helpers.store :as store]
            [ctim.examples.target-records :refer [new-target-record-minimal
                                                  new-target-record-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn additional-tests [app _ target-record-sample]
  (testing "GET /ctia/target-record/search"
   (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
    ;; only when ES store
    (when (= "es" (get-in-config [:ctia :store :target-record]))
      (let [{[target] :targets} target-record-sample
            {[observable] :observables} target]
        (are [term check-fn expected desc] (let [response (helpers/get
                                                           "ctia/target-record/search"
                                                           :query-params {"query" term}
                                                           :headers {"Authorization" "45c1f5e3f05d0"})]
                                             (is (= 200 (:status response)))
                                             (is (= expected (check-fn response)) desc)

                                             ;; to prevent `are` from double-failing
                                             true)

          (format "targets.type:%s" (:type target))
          (fn [r] (-> r :parsed-body first :targets first :type))
          (:type target)
          "Searching for target-record:targets:type"

          (format "targets.observables.value:%s" (:value observable))
          (fn [r] (-> r :parsed-body first :targets first :observables first :value))
          (:value observable)
          "Searching for target-record:targets:observables:value"

          (format "targets.observables.type:%s" (:type observable))
          (fn [r] (-> r :parsed-body first :targets first :observables first :type))
          (:type observable)
          "Searching for target-record:targets:observables:type"))))))

(deftest target-record-routes-test
  (store/test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response http/api-key "foouser" "foogroup" "user")
     (entity-crud-test
      {:app              app
       :entity           "target-record"
       :example          new-target-record-maximal
       :invalid-tests?   true
       :update-tests?    true
       :update-field     :source
       :search-tests?    true
       :additional-tests additional-tests
       :headers          {:Authorization "45c1f5e3f05d0"}}))))

(deftest target-record-pagination-test
  (store/test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [ids (helpers/post-entity-bulk
                new-target-record-maximal
                :target_records
                30
                {"Authorization" "45c1f5e3f05d0"})]

       (field-selection/field-selection-tests
        ["ctia/target-record/search?query=*"
         (http/doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        target-record/target-record-fields)

       (pagination/pagination-test
        "ctia/target-record/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        target-record/target-record-fields)))))

(deftest target-record-metric-routes-test
  (aggregate/test-metric-routes
   (into target-record/target-record-entity
         {:plural            :target_records
          :entity-minimal    new-target-record-minimal
          :enumerable-fields target-record/target-record-enumerable-fields
          :date-fields       target-record/target-record-histogram-fields})))
