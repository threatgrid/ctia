(ns ctia.http.routes.incident-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clj-momo.lib.clj-time.core :as t]
            [clj-momo.lib.clj-time.coerce :as tc]
            [clojure.test :refer [deftest is testing join-fixtures use-fixtures]]
            [ctia.entity.incident :refer [incident-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-entity-bulk post]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store]]]
            [ctim.examples.incidents
             :refer
             [new-incident-maximal new-incident-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn partial-operations-tests [incident-id incident]
  (testing "POST /ctia/incident/:id/status Open"
    (let [new-status {:status "Open"}
          response (post (str "ctia/incident/" (:short-id incident-id) "/status")
                         :body new-status
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-incident (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= "Open" (:status updated-incident)))
      (is (get-in updated-incident [:incident_time :opened]))

      (is (t/within?
           (t/interval (t/minus (t/now) (t/minutes 10))
                       (t/plus (t/now) (t/minutes 10)))
           (tc/from-date (get-in updated-incident [:incident_time :opened]))))))

  (testing "POST /ctia/incident/:id/status Closed"
    (let [new-status {:status "Closed"}
          response (post (str "ctia/incident/" (:short-id incident-id) "/status")
                         :body new-status
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-incident (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= "Closed" (:status updated-incident)))
      (is (get-in updated-incident [:incident_time :closed]))

      (is (t/within?
           (t/interval (t/minus (t/now) (t/minutes 10))
                       (t/plus (t/now) (t/minutes 10)))
           (tc/from-date (get-in updated-incident [:incident_time :closed]))))))

  (testing "POST /ctia/incident/:id/status Containment Achieved"
    (let [new-status {:status "Containment Achieved"}
          response (post (str "ctia/incident/" (:short-id incident-id) "/status")
                         :body new-status
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-incident (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= "Containment Achieved" (:status updated-incident)))
      (is (get-in updated-incident [:incident_time :remediated]))

      (is (t/within?
           (t/interval (t/minus (t/now) (t/minutes 10))
                       (t/plus (t/now) (t/minutes 10)))
           (tc/from-date (get-in updated-incident [:incident_time :remediated])))))))

(deftest test-incident-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "foogroup" "user")
     (entity-crud-test
      {:entity "incident"
       :patch-tests? true
       :example new-incident-maximal
       :headers {:Authorization "45c1f5e3f05d0"}
       :additional-tests partial-operations-tests}))))

(deftest test-incident-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [ids (post-entity-bulk
                (assoc new-incident-maximal :title "foo")
                :incidents
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        "ctia/incident/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        incident-fields)

       (field-selection-tests
        ["ctia/incident/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        incident-fields)))))

(deftest test-incident-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "incident"
                          new-incident-minimal
                          true
                          true))))
