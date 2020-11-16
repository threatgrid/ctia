(ns ctia.entity.incident-test
  (:require [clj-momo.lib.clj-time
             [coerce :as tc]
             [core :as t]]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.entity.incident :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [PATCH POST POST-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.incidents
             :refer
             [new-incident-maximal new-incident-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(defn partial-operations-tests [app incident-id incident]
  (let [fixed-now (t/internal-now)]
    (helpers/with-fixed-time app fixed-now
      (testing "Incident status update: test setup"
        (let [response (PATCH app
                              (str "ctia/incident/" (:short-id incident-id))
                              :body {:incident_time {}}
                              :headers {"Authorization" "45c1f5e3f05d0"})
              updated-incident (:parsed-body response)]
          (is (= 200 (:status response)))))
 
      (testing "POST /ctia/incident/:id/status Open"
        (let [new-status {:status "Open"}
              response (POST app
                             (str "ctia/incident/" (:short-id incident-id) "/status")
                             :body new-status
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-incident (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= "Open" (:status updated-incident)))
          (is (get-in updated-incident [:incident_time :opened]))
 
          (is (= (get-in updated-incident [:incident_time :opened])
                 (tc/to-date fixed-now)))))
 
      (testing "POST /ctia/incident/:id/status Closed"
        (let [new-status {:status "Closed"}
              response (POST app
                             (str "ctia/incident/" (:short-id incident-id) "/status")
                             :body new-status
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-incident (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= "Closed" (:status updated-incident)))
          (is (get-in updated-incident [:incident_time :closed]))
 
          (is (= (get-in updated-incident [:incident_time :closed])
                 (tc/to-date fixed-now)))))
 
      (testing "POST /ctia/incident/:id/status Containment Achieved"
        (let [new-status {:status "Containment Achieved"}
              response (POST app
                             (str "ctia/incident/" (:short-id incident-id) "/status")
                             :body new-status
                             :headers {"Authorization" "45c1f5e3f05d0"})
              updated-incident (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= "Containment Achieved" (:status updated-incident)))
          (is (get-in updated-incident [:incident_time :remediated]))
 
          (is (= (get-in updated-incident [:incident_time :remediated])
                 (tc/to-date fixed-now))))))))
 
(deftest test-incident-crud-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
     (let [parameters {:app app
                       :entity "incident"
                       :patch-tests? true
                       :example new-incident-maximal
                       :headers {:Authorization "45c1f5e3f05d0"}
                       :additional-tests partial-operations-tests}]
       (entity-crud-test parameters)))))

(deftest test-incident-metric-routes
  (test-metric-routes (into sut/incident-entity
                            {:entity-minimal new-incident-minimal
                             :enumerable-fields sut/incident-enumerable-fields
                             :date-fields sut/incident-histogram-fields})))

(deftest test-incident-pagination-field-selection
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
                (assoc new-incident-maximal :title "foo")
                :incidents
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        app
        "ctia/incident/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sut/incident-fields)

       (field-selection-tests
        app
        ["ctia/incident/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/incident-fields)))))

(deftest test-incident-routes-access-control
  (access-control-test "incident"
                       new-incident-minimal
                       true
                       true
                       test-for-each-store-with-app))
