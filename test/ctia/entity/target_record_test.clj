(ns ctia.entity.target-record-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is are testing use-fixtures]]
            [ctia.entity.target-record :as sut]
            [ctia.test-helpers.aggregate :as aggregate]
            [ctia.test-helpers.auth :as auth]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.http :as http]
            [ctia.test-helpers.store :as store]
            [ctim.examples.target-records :refer [new-target-record-minimal
                                                  new-target-record-maximal]]))

(use-fixtures :once
              mth/fixture-schema-validation
              whoami-helpers/fixture-server)

(def enabled-stores #{:tool :target-record :attack-pattern :incident :casebook :malware})

(defn additional-tests [app _ target-record-sample]
  (testing "GET /ctia/target-record/search"
   (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
    ;; only when ES store
    (when (= "es" (get-in-config [:ctia :store :target-record]))
      (let [{[target] :targets} target-record-sample
            {[observable] :observables} target]
        (are [term check-fn expected desc] (let [response (helpers/GET
                                                           app
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
   enabled-stores
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response app http/api-key "foouser" "foogroup" "user")
     (entity-crud-test
      (into sut/target-record-entity
            {:app              app
             :example          new-target-record-maximal
             :invalid-tests?   true
             :update-tests?    true
             :update-field     :source
             :additional-tests additional-tests
             :headers          {:Authorization "45c1f5e3f05d0"}})))))

(deftest target-record-metric-routes-test
  (aggregate/test-metric-routes
   enabled-stores
   (into sut/target-record-entity
         {:plural            :target_records
          :entity-minimal    new-target-record-minimal
          :enumerable-fields sut/target-record-enumerable-fields
          :date-fields       sut/target-record-histogram-fields})))
