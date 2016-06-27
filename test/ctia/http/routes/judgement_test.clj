(ns ctia.http.routes.judgement-test
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [properties]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post]]
             [fake-whoami-service :as whoami-helpers]
             [pagination :refer [pagination-test]]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-judgement-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (helpers/set-capabilities! "baruser" "user" #{})
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")
  (whoami-helpers/set-whoami-response "2222222222222" "baruser" "user")

  (testing "POST /ctia/judgement"
    (let [{judgement :parsed-body
           status :status}
          (post "ctia/judgement"
                :body {:observable {:value "1.2.3.4"
                                    :type "ip"}
                       :disposition 2
                       :source "test"
                       :priority 100
                       :severity 100
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                       :indicators [{:confidence "High"
                                     :source "source"
                                     :relationship "relationship"
                                     :indicator_id "indicator-123"}]}
                :headers {"api_key" "45c1f5e3f05d0"})
          judgement-id (id/short-id->id :judgement
                                        (:id judgement)
                                        (get-in @properties [:ctia :http :show]))]
      (is (= 201 status))
      (is (deep=
           {:type "judgement"
            :observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :priority 100
            :severity 100
            :confidence "Low"
            :source "test"
            :tlp "green"
            :schema_version schema-version
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :indicators [{:confidence "High"
                          :source "source"
                          :relationship "relationship"
                          :indicator_id "indicator-123"}]
            :owner "foouser"}
           (dissoc judgement
                   :id
                   :created)))

      (testing "GET /ctia/judgement/:id"
        (let [response (get (str "ctia/judgement/" (:id judgement))
                            :headers {"api_key" "45c1f5e3f05d0"})
              judgement (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:type "judgement"
                :observable {:value "1.2.3.4"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :priority 100
                :severity 100
                :confidence "Low"
                :source "test"
                :tlp "green"
                :schema_version schema-version
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :indicators [{:confidence "High"
                              :source "source"
                              :relationship "relationship"
                              :indicator_id "indicator-123"}]
                :owner "foouser"}
               (dissoc judgement
                       :id
                       :created)))))

      (testing "GET /ctia/judgement/:id with query-param api_key"
        (let [{status :status
               judgement :parsed-body
               :as response}
              (get (str "ctia/judgement/" (:id judgement))
                   :query-params {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 (:status response)))
          (is (deep=
               {:type "judgement"
                :observable {:value "1.2.3.4"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :priority 100
                :severity 100
                :confidence "Low"
                :source "test"
                :tlp "green"
                :schema_version schema-version
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :indicators [{:confidence "High"
                              :source "source"
                              :relationship "relationship"
                              :indicator_id "indicator-123"}]
                :owner "foouser"}
               (dissoc judgement
                       :id
                       :created)))))

      (testing "GET /ctia/judgement/:id authentication failures"
        (testing "no api_key"
          (let [{body :parsed-body status :status}
                (get (str "ctia/judgement/" (:id judgement)))]
            (is (= 403 status))
            (is (= {:message "Only authenticated users allowed"} body))))

        (testing "unknown api_key"
          (let [{body :parsed-body status :status}
                (get (str "ctia/judgement/" (:id judgement))
                     :headers {"api_key" "1111111111111"})]
            (is (= 403 status))
            (is (= {:message "Only authenticated users allowed"} body))))

        (testing "doesn't have read capability"
          (let [{body :parsed-body status :status}
                (get (str "ctia/judgement/" (:id judgement))
                     :headers {"api_key" "2222222222222"})]
            (is (= 401 status))
            (is (= {:message "Missing capability",
                    :capabilities :read-judgement,
                    :owner "baruser"}
                   body)))))

      (testing "DELETE /ctia/judgement/:id"
        (let [temp-judgement (-> (post "ctia/judgement"
                                       :body {:indicators [{:indicator_id "indicator-123"}]
                                              :observable {:value "9.8.7.6"
                                                           :type "ip"}
                                              :disposition 3
                                              :source "test"
                                              :priority 100
                                              :severity 100
                                              :confidence "Low"
                                              :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                                       :headers {"api_key" "45c1f5e3f05d0"})
                                 :parsed-body)
              response (delete (str "ctia/judgement/" (:id temp-judgement))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/judgement/" (:id temp-judgement))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))

(deftest-for-each-store test-judgement-routes-for-dispositon-determination
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST a judgement with dispositon (id)"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement (:parsed-body response)]
      (is (= 201 (:status response)))
      (is (deep=
           {:type "judgement"
            :observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :source "test"
            :priority 100
            :severity 100
            :confidence "Low"
            :tlp "green"
            :schema_version schema-version
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc judgement
                   :id
                   :created)))))

  (testing "POST a judgement with disposition_name"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition_name "Malicious"
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement (:parsed-body response)]
      (is (= 201 (:status response)))
      (is (deep=
           {:type "judgement"
            :observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :source "test"
            :priority 100
            :severity 100
            :confidence "Low"
            :tlp "green"
            :schema_version schema-version
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc judgement
                   :id
                   :created)))))

  (testing "POST a judgement without disposition"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement (:parsed-body response)]
      (is (= 201 (:status response)))
      (is (deep=
           {:type "judgement"
            :observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 5
            :disposition_name "Unknown"
            :source "test"
            :priority 100
            :severity 100
            :confidence "Low"
            :tlp "green"
            :schema_version schema-version
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc judgement
                   :id
                   :created)))))

  (testing "POST a judgement with mismatching disposition/disposition_name"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition 1
                                :disposition_name "Unknown"
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 400 (:status response)))
      (is (deep=
           {:error "Mismatching :dispostion and dispositon_name for judgement",
            :judgement {:observable {:value "1.2.3.4"
                                     :type "ip"}
                        :disposition 1
                        :disposition_name "Unknown"
                        :source "test"
                        :priority 100
                        :severity 100
                        :confidence "Low"
                        :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}}}
           (:parsed-body response))))))

(deftest-for-each-store test-list-judgements-by-observable-pagination
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (dotimes [n 30]
    (let [{status :status}
          (post "ctia/judgement"
                :body {:observable {:value "1.2.3.4"
                                    :type "ip"}
                       :disposition 5
                       :disposition_name "Unknown"
                       :source (str "dotimes " n)
                       :priority 100
                       :severity 100
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                :headers {"api_key" "45c1f5e3f05d0"})]
      (assert (= 201 status)
              (format "Expected status to be 200 but was %s on loop %s" status n))))

  (pagination-test
   "ctia/ip/1.2.3.4/judgements"
   {"api_key" "45c1f5e3f05d0"}
   [:id :disposition :priority :severity :confidence]))
