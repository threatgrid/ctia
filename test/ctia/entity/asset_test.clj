(ns ctia.entity.asset-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is are join-fixtures testing use-fixtures]]
            [ctia.test-helpers.auth :as auth]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.http :as http]
            [ctia.test-helpers.store :refer [test-for-each-store]]
            [ctim.examples.assets :refer [new-asset-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn additional-tests [asset-id asset-sample]
  (testing "GET /ctia/asset/search"
    ;; only when ES store
    (when (= "es" (get-in @ctia.properties/properties [:ctia :store :asset]))
      (are [term check-fn expected desc] (let [response (helpers/get (str "ctia/asset/search")
                                                                     :query-params {"query" term}
                                                                     :headers {"Authorization" "45c1f5e3f05d0"})]
                                           (is (= 200 (:status response)))
                                           (is (= expected (check-fn response)) desc))

        "asset_type:\"Device\""
        (fn [r] (-> r :parsed-body first :asset_type))
        (-> asset-sample :asset_type)
        "Searching by an Asset type works"

        ;; TODO: Add more cases

        ))))

(deftest asset-routes-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response http/api-key "foouser" "foogroup" "user")
     (entity-crud-test
      {:entity           "asset"
       :example          new-asset-maximal
       :invalid-tests?   false
       :update-tests?    true
       :search-tests?    false
       :update-field     :source
       :additional-tests additional-tests
       :headers          {:Authorization "45c1f5e3f05d0"}}))))

