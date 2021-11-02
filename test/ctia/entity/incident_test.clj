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
             [core :as helpers :refer [PATCH POST]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.incidents
             :refer
             [new-incident-maximal new-incident-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(defn partial-operations-tests [app incident-id incident]
  (let [fixed-now (t/internal-now)]
    (helpers/fixture-with-fixed-time
     fixed-now
     (fn []
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
                  (tc/to-date fixed-now)))))
       (let [client_id-cases (s/validate
                               [{:query-string s/Str
                                 :client_id? s/Boool}]
                               (concat
                                 (map #(hash-map :query-string % :client_id? true)
                                      ["?client_id=true" "?wait_for=true&client_id=true"
                                       "?wait_for=true&client_id=false&client_id=true"])
                                 (map #(hash-map :query-string % :client_id? false)
                                      ["" "?client_id=false" "?wait_for=true&client_id=false"
                                       "?wait_for=true&client_id=true&client_id=false"])))]
         (doseq [{:keys [query-string client_id?] :as test-case} client_id-cases
                 :let [expected-result (if client_id? {:client_id "Containment Achieved"} {})]]
           (testing (pr-str test-case)
             (testing "GET /ctia/incident/:id?client_id=..."
               (let [response (GET app
                                   (str "ctia/incident/" (:short-id incident-id) query-string)
                                   :headers {"Authorization" "45c1f5e3f05d0"})
                     incident (:parsed-body response)]
                 (is (= 200 (:status response)))
                 (is (= expected-result (select-keys incident [:client_id])))))
             (testing "PATCH /ctia/incident/:id?client_id=..."
               (let [response (PATCH app
                                     (str "ctia/incident/" (:short-id incident-id) query-string)
                                     :body {:incident_time {}}
                                     :headers {"Authorization" "45c1f5e3f05d0"})
                     incident (:parsed-body response)]
                 (is (= 200 (:status response)))
                 (is (= expected-result (select-keys incident [:client_id]))))))))))))

(deftest test-incident-crud-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
     (let [parameters (into sut/incident-entity
                            {:app app
                             :patch-tests? true
                             :example new-incident-maximal
                             :headers {:Authorization "45c1f5e3f05d0"}
                             :additional-tests partial-operations-tests})]
       (entity-crud-test parameters)))))

(deftest test-incident-metric-routes
  (test-metric-routes (into sut/incident-entity
                            {:entity-minimal new-incident-minimal
                             :enumerable-fields sut/incident-enumerable-fields
                             :date-fields sut/incident-histogram-fields})))

(deftest test-incident-routes-access-control
  (access-control-test "incident"
                       new-incident-minimal
                       true
                       true
                       test-for-each-store-with-app))
