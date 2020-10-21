(ns ctia.entity.identity-assertion-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is testing join-fixtures use-fixtures]]
            [ctia.entity.identity-assertion :as sut]
            [ctia.properties :as p]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [POST-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [api-key doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [core :as helpers :refer [GET]]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.identity-assertions
             :refer
             [new-identity-assertion-maximal new-identity-assertion-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(def new-identity-assertion
  (-> new-identity-assertion-maximal
      (dissoc :id)
      (assoc
       :tlp "green"
       :external_ids
       ["http://ex.tld/ctia/identity-assertion/identity-assertion-123"
        "http://ex.tld/ctia/identity-assertion/identity-assertion-345"])))

(defn additional-tests [app identity-assertion-id _]
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
      {:app app
       :entity "identity-assertion"
       :example new-identity-assertion-maximal
       :invalid-tests? false
       :update-tests? true
       :search-tests? false
       :update-field :source
       :additional-tests additional-tests
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-identity-assertion-pagination-field-selection
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [ids (POST-entity-bulk
                app
                new-identity-assertion-maximal
                :identity_assertions
                30
                {"Authorization" "45c1f5e3f05d0"})]

       (field-selection-tests
        app
        ["ctia/identity-assertion/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/identity-assertion-fields)


       (pagination-test
        app
        "ctia/identity-assertion/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sut/identity-assertion-fields)))))

(deftest test-identity-assertion-metric-routes
  (test-metric-routes (into sut/identity-assertion-entity
                            {:plural :identity_assertions
                             :entity-minimal new-identity-assertion-minimal
                             :enumerable-fields sut/identity-assertion-enumerable-fields
                             :date-fields sut/identity-assertion-histogram-fields})))
