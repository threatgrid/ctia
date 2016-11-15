(ns ctia.http.routes.verdict-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.lib.time :as time]
            [clj-momo.test-helpers.core :as mht]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [get post]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]))

(use-fixtures :once (join-fixtures [mht/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    helpers/fixture-properties:events-enabled
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-observable-verdict-route
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "test setup: create a judgement (1)"
    ;; Incorrect observable
    (let [response (post "ctia/judgement"
                         :body {:indicators []
                                :observable {:value "127.0.0.1"
                                             :type "ip"}
                                :disposition 1
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 201 (:status response)))))

  (testing "test setup: create a judgement (2)"
    ;; Lower priority
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 1
                                :source "test"
                                :priority 90
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 201 (:status response)))))

  (testing "test setup: create a judgement (3)"
    ;; Wrong disposition
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 3
                                :source "test"
                                :priority 99
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 201 (:status response)))))


  (testing "a verdict that doesn't exist is a 404"
    (let [{status :status}
          (get "ctia/ip/10.0.0.42/verdict"
               :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 404 status))))

  (testing "test setup: create a judgement (4)"
    ;; Loses a tie because of its timestamp being later
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 99
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:01:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement-1 (:parsed-body response)]
      (is (= 201 (:status response)))))

  (testing "with a highest-priority judgement"
    (let [{status :status
           judgement :parsed-body}
          (post "ctia/judgement"
                :body {:observable {:value "10.0.0.1"
                                    :type "ip"}
                       :disposition 2
                       :source "test"
                       :priority 99
                       :severity 100
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                :headers {"api_key" "45c1f5e3f05d0"})

          judgment-id
          (id/long-id->id (:id judgement))]
      (is (= 201 status)) ;; success creating judgement

      (testing "GET /ctia/:observable_type/:observable_value/verdict"
        (let [{status :status
               verdict :parsed-body}
              (get "ctia/ip/10.0.0.1/verdict"
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (= {:id (str "verdict-" (-> (:short-id judgment-id) (subs 10)))
                  :type "verdict"
                  :disposition 2
                  :disposition_name "Malicious"
                  :judgement_id (:id judgement)
                  :observable {:value "10.0.0.1", :type "ip"}}
                 (dissoc verdict :owner :created :schema_version))))))))

(deftest-for-each-store test-observable-verdict-route-2
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  ;; This test case catches a bug that was in the atom store
  ;; It tests the code path where priority is equal but dispositions differ
  (testing "test setup: create a judgement (1)"
    (let [{status :status}
          (post "ctia/judgement"
                :body {:observable {:value "string",
                                    :type "device"},
                       :reason_uri "string",
                       :source "string",
                       :disposition 1,
                       :reason "string",
                       :source_uri "string",
                       :priority 99,
                       :severity 50,
                       :valid_time {:start_time "2016-02-12T14:56:26.814-00:00"
                                    :end_time "2016-02-12T14:56:26.719-00:00"}
                       :confidence "Medium"}
                :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 201 status))))

  (testing "with a verdict judgement"
    (let [{status :status
           judgement :parsed-body}
          (post "ctia/judgement"
                :body {:observable {:value "10.0.0.1",
                                    :type "ip"},
                       :reason_uri "string",
                       :source "string",
                       :disposition 2,
                       :reason "string",
                       :source_uri "string",
                       :priority 99,
                       :severity 50,
                       :valid_time {:start_time "2016-02-12T14:56:26.814-00:00"}
                       :confidence "Medium"}
                :headers {"api_key" "45c1f5e3f05d0"})

          judgement-id
          (id/long-id->id (:id judgement))]
      (is (= 201 status))

      (testing "GET /ctia/:observable_type/:observable_value/verdict"
        (with-redefs [time/now (constantly (time/timestamp "2016-02-12T15:42:58.232-00:00"))]
          (let [{status :status
                 verdict :parsed-body}
                (get "ctia/ip/10.0.0.1/verdict"
                     :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 200 status))
            (is (= {:id (str "verdict-" (-> (:short-id judgement-id) (subs 10)))
                    :observable {:value "10.0.0.1",:type "ip"}
                    :type "verdict"
                    :disposition 2
                    :disposition_name "Malicious"
                    :judgement_id (:id judgement)}
                   (dissoc verdict :owner :created :schema_version)))))))))
