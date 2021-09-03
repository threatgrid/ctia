(ns ctia.entity.incident-test
  (:require [clj-momo.lib.clj-time.coerce :as tc]
            [clj-momo.lib.clj-time.core :as t]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.entity.incident :as sut]
            [ctia.entity.incident.schemas
             :refer [incident-enumerable-fields incident-histogram-fields]]
            [ctia.test-helpers.access-control :refer [access-control-test]]
            [ctia.test-helpers.aggregate :refer [test-metric-routes]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers :refer [PATCH POST GET]]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.examples.incidents
             :refer [new-incident-maximal new-incident-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(defn partial-operations-tests [app incident-id]
  (let [fixed-now (t/internal-now)]
    (helpers/fixture-with-fixed-time
     fixed-now
     (fn []
       (testing "Incident status update: test setup"
         (let [response (PATCH app
                               (str "ctia/incident/" (:short-id incident-id))
                               :body {:incident_time {}}
                               :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 200 (:status response)))))

       (testing "POST /ctia/incident/:id/status Open"
         (let [new-status {:status "Open"}
               response (POST app
                              (str "ctia/incident/" (:short-id incident-id) "/status")
                              :body new-status
                              :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body response)]
           (clojure.pprint/pprint response)
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
                  (tc/to-date fixed-now)))))))))

(defn test-high-impact-incident
  [app incident]
  (let [not-high-impact (:parsed-body
                         (POST app
                               "ctia/incident"
                               :body (assoc incident :source "ngfw")
                               :headers {"Authorization" "45c1f5e3f05d0"}))
        high-impact (:parsed-body
                     (POST app
                           "ctia/incident?wait_for=true"
                           :body (assoc new-incident-minimal
                                        :source "secure endpoint")
                           :headers {"Authorization" "45c1f5e3f05d0"}))
        _ (assert (map? not-high-impact))
        _ (assert (map? high-impact))
        test-fn (fn [{:keys [msg high-impact? expected-entities]}]
                  (testing msg
                    (let [path (cond-> "ctia/incident/search"
                                 (some? high-impact?) (str "?high_impact=" high-impact?))
                          search-res (GET app
                                          path
                                          :headers {"Authorization" "45c1f5e3f05d0"})
                          found-ids (->> search-res :parsed-body (map :id))]
                      (is (= 200 (:status search-res)))
                      (is (= (set (map :id expected-entities))
                             (set found-ids))))))
        test-plan [{:msg "Only high impact incidents shall be returned when high_impact is true"
                    :high-impact? true
                    :expected-entities [high-impact]}
                   {:msg "Only non high impact incidents shall be returned when high_impact is false"
                    :high-impact? false
                    :expected-entities [not-high-impact]}
                   {:msg "The impact of an incident is ignored by default"
                    :high-impact? nil
                    :expected-entities [high-impact not-high-impact]}]]
    (doseq [test-case test-plan]
      (test-fn test-case))))

(defn crud-additional-tests
  [app incident-id incident]
  (partial-operations-tests app incident-id)
  (test-high-impact-incident app incident))

(deftest test-incident-crud-routes
  (helpers/with-properties
    ["ctia.incident.high-impact.source" "secure endpoint"]
    (test-for-each-store-with-app
     (fn [app]
       (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
       (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
       (let [parameters (into sut/incident-entity
                              {:app app
                               :patch-tests? true
                               :example new-incident-maximal
                               :headers {:Authorization "45c1f5e3f05d0"}
                               :additional-tests crud-additional-tests})]
         (entity-crud-test parameters))))))

(deftest test-incident-metric-routes
  (test-metric-routes (into sut/incident-entity
                            {:entity-minimal new-incident-minimal
                             :enumerable-fields incident-enumerable-fields
                             :date-fields incident-histogram-fields})))

(deftest test-incident-routes-access-control
  (access-control-test "incident"
                       new-incident-minimal
                       true
                       true
                       test-for-each-store-with-app))
