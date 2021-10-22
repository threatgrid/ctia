(ns ctia.entity.identity-assertion-test
  (:require [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.entity.identity-assertion :as sut]
            [ctia.test-helpers.aggregate :refer [test-metric-routes]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers :refer [GET]]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.http :refer [api-key]]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.examples.identity-assertions
             :refer
             [new-identity-assertion-maximal new-identity-assertion-minimal]]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :once (join-fixtures [validate-schemas
                                    whoami-helpers/fixture-server]))

(def new-identity-assertion
  (-> new-identity-assertion-maximal
      (dissoc :id)
      (assoc
       :tlp "green"
       :external_ids
       ["http://ex.tld/ctia/identity-assertion/identity-assertion-123"
        "http://ex.tld/ctia/identity-assertion/identity-assertion-345"])))

(defn additional-tests [app _identity-assertion-id _]
  (testing "GET /ctia/identity-assertion/search"
    (do
      (let [term "identity.observables.value:\"1.2.3.4\""
            response (GET app
                          (str "ctia/identity-assertion/search")
                          :query-params {"query" term}
                          :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 200 (:status response)) "IP quoted term works"))

      (let [term "1.2.3.4"
            response (GET app
                          (str "ctia/identity-assertion/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)) "IP unquoted, term works"))

      (let [term "assertions.name:\"cisco:ctr:device:id\""
            response (GET app
                          (str "ctia/identity-assertion/search")
                          :query-params {"query" term}
                          :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 200 (:status response)) "Search by Assertion name term works"))

      (let [term "*"
            response (GET app
                          (str "ctia/identity-assertion/search")
                          :query-params {"query" term
                                         "assertions.name" "cisco:ctr:device:id"}
                          :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 200 (:status response)) "Search by Assertion term works")))))

(deftest test-identity-assertion-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         api-key
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      (into sut/identity-assertion-entity
            {:app app
             :example new-identity-assertion-maximal
             :invalid-tests? false
             :update-tests? true
             :search-tests? false
             :update-field :source
             :additional-tests additional-tests
             :headers {:Authorization "45c1f5e3f05d0"}})))))

(deftest test-identity-assertion-metric-routes
  (test-metric-routes (into sut/identity-assertion-entity
                            {:plural :identity_assertions
                             :entity-minimal new-identity-assertion-minimal
                             :enumerable-fields sut/identity-assertion-enumerable-fields
                             :date-fields sut/identity-assertion-histogram-fields})))
