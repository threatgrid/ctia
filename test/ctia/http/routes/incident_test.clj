(ns ctia.http.routes.incident-test
  (:refer-clojure :exclude [get])
  (:require
    [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
    [ctia.domain.entities :refer [schema-version]]
    [ctia.test-helpers.core :refer [delete get post put] :as helpers]
    [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
    [ctia.test-helpers.store :refer [deftest-for-each-store]]
    [ctia.test-helpers.auth :refer [all-capabilities]]
    [ctim.schemas
     [common :as c]
     [incident :refer [NewIncident StoredIncident]]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-incident-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/incident"
    (let [response (post "ctia/incident"
                         :body {:title "incident"
                                :description "description"
                                :confidence "High"
                                :categories ["Denial of Service"
                                             "Improper Usage"]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                                :related_indicators [{:confidence "High"
                                                      :source "source"
                                                      :relationship "relationship"
                                                      :indicator_id "indicator-123"}]
                                :related_incidents [{:incident_id "incident-123"}
                                                    {:incident_id "indicent-789"}]}
                         :headers {"api_key" "45c1f5e3f05d0"})
          incident (:parsed-body response)]
      (is (= 201 (:status response)))
      (is (deep=
           {:type "incident"
            :title "incident"
            :description "description"
            :tlp "green"
            :schema_version schema-version
            :confidence "High"
            :categories ["Denial of Service"
                         "Improper Usage"]
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :related_indicators [{:confidence "High"
                                  :source "source"
                                  :relationship "relationship"
                                  :indicator_id "indicator-123"}]

            :related_incidents [{:incident_id "incident-123"}
                                {:incident_id "indicent-789"}]
            :owner "foouser"}
           (dissoc incident
                   :id
                   :created
                   :modified)))

      (testing "GET /ctia/incident/:id"
        (let [response (get (str "ctia/incident/" (:id incident))
                            :headers {"api_key" "45c1f5e3f05d0"})
              incident (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:type "incident"
                :title "incident"
                :description "description"
                :tlp "green"
                :schema_version schema-version
                :confidence "High"
                :categories ["Denial of Service"
                             "Improper Usage"]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :related_indicators [{:confidence "High"
                                      :source "source"
                                      :relationship "relationship"
                                      :indicator_id "indicator-123"}]
                :related_incidents [{:incident_id "incident-123"}
                                    {:incident_id "indicent-789"}]
                :owner "foouser"}
               (dissoc incident
                       :id
                       :created
                       :modified)))))

      (testing "PUT /ctia/incident/:id"
        (let [{status :status
               updated-incident :parsed-body}
              (put (str "ctia/incident/" (:id incident))
                   :body {:title "updated incident"
                          :description "updated description"
                          :tlp "green"
                          :confidence "Low"
                          :categories ["Denial of Service"
                                       "Improper Usage"]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                          :related_indicators [{:confidence "High"
                                                :source "another source"
                                                :relationship "relationship"
                                                :indicator_id "indicator-234"}]
                          :related_incidents [{:incident_id "incident-123"}
                                              {:incident_id "indicent-789"}]}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:type "incident"
                :id (:id incident)
                :created (:created incident)
                :title "updated incident"
                :description "updated description"
                :tlp "green"
                :schema_version schema-version
                :confidence "Low"
                :categories ["Denial of Service"
                             "Improper Usage"]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :related_indicators [{:confidence "High"
                                      :source "another source"
                                      :relationship "relationship"
                                      :indicator_id "indicator-234"}]
                :related_incidents [{:incident_id "incident-123"}
                                    {:incident_id "indicent-789"}]
                :owner "foouser"}
               (dissoc updated-incident
                       :modified)))))

      (testing "DELETE /ctia/incident/:id"
        (let [response (delete (str "ctia/incident/" (:id incident))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/incident/" (:id incident))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
