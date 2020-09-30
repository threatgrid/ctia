(ns ctia.entity.indicator-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is testing are join-fixtures use-fixtures]]
            [ctia.auth.capabilities :as caps]
            [ctia.entity.indicator :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [core :as helpers]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.indicators
             :refer
             [new-indicator-maximal new-indicator-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)


(defn search-tests [app _ indicator-sample]
  (testing "GET /ctia/indicator/search"
   (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
    ;; only when ES store
    (when (= "es" (get-in-config [:ctia :store :indicator]))
      (are [query check-fn expected desc]
          (testing desc

            (let [response (helpers/GET
                            app
                            "ctia/indicator/search"
                            :query-params query
                            :headers {"Authorization" "45c1f5e3f05d0"})]

              (is (= 200 (:status response)))
              (is (= (check-fn expected)))
            true))

        {:tags "foo"}
        #(-> % :parsed-body first :tags)
        (:tags indicator-sample)
        "Searching indicators by tag `foo` should match example"

        {:tags "bar"}
        #(-> % :parsed-body first :tags)
        (:tags indicator-sample)
        "Searching indicators by tag `bar` should match example"

        {:tags "unmatched"}
        #(:parsed-body %)
        []
        "Searching indicators by missing tags value should not match any document")))))

(deftest test-indicator-crud-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" caps/all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      {:app app
       :entity "indicator"
       :example new-indicator-maximal
       :additional-tests search-tests
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-indicator-routes-access-control
  (access-control-test "indicator"
                       new-indicator-minimal
                       true
                       false
                       test-for-each-store-with-app))

(deftest test-indicator-metric-routes
  (test-metric-routes (into sut/indicator-entity
                            {:entity-minimal new-indicator-minimal
                             :enumerable-fields sut/indicator-enumerable-fields
                             :date-fields sut/indicator-histogram-fields})))

