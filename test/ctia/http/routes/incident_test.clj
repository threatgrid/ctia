(ns ctia.http.routes.incident-test
  (:refer-clojure :exclude [get])
  (:require
    [clj-momo.test-helpers.core :as mth]
    [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
    [ctia.domain.entities :refer [schema-version]]
    [ctia.properties :refer [get-http-show]]
    [ctia.test-helpers
     [core :as helpers :refer [delete get post put]]
     [fake-whoami-service :as whoami-helpers]
     [store :refer [deftest-for-each-store]]
     [auth :refer [all-capabilities]]]
    [ctim.domain.id :as id]
    [ctim.schemas
     [common :as c]
     [incident :refer [NewIncident StoredIncident]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-incident-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/incident"
    (let [{status :status
           incident :parsed-body}
          (post "ctia/incident"
                :body {:external_ids ["http://ex.tld/ctia/incident/incident-123"
                                      "http://ex.tld/ctia/incident/incident-456"]
                       :title "incident"
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

          incident-id (id/long-id->id (:id incident))
          incident-external-ids (:external_ids incident)]
      (is (= 201 status))
      (is (deep=
           {:id (id/long-id incident-id)
            :external_ids ["http://ex.tld/ctia/incident/incident-123"
                           "http://ex.tld/ctia/incident/incident-456"]
            :type "incident"
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
                   :created
                   :modified)))

      (testing "the incident ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    incident-id)      (:hostname    show-props)))
          (is (= (:protocol    incident-id)      (:protocol    show-props)))
          (is (= (:port        incident-id)      (:port        show-props)))
          (is (= (:path-prefix incident-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/incident/external_id"
        (let [response (get "ctia/incident/external_id"
                            :headers {"api_key" "45c1f5e3f05d0"}
                            :query-params {"external_id" (rand-nth incident-external-ids)})
              incidents (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id incident-id)
                 :external_ids ["http://ex.tld/ctia/incident/incident-123"
                                "http://ex.tld/ctia/incident/incident-456"]
                 :type "incident"
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
                 :owner "foouser"}]
               (map #(dissoc % :created :modified) incidents)))))

      ;;(test-query-string-search :incident "description" :description)
      
      (testing "GET /ctia/incident/:id"
        (let [response (get (str "ctia/incident/" (:short-id incident-id))
                            :headers {"api_key" "45c1f5e3f05d0"})
              incident (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id incident-id)
                :external_ids ["http://ex.tld/ctia/incident/incident-123"
                               "http://ex.tld/ctia/incident/incident-456"]
                :type "incident"
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
                       :created
                       :modified)))))

      (testing "PUT /ctia/incident/:id"
        (let [{status :status
               updated-incident :parsed-body}
              (put (str "ctia/incident/" (:short-id incident-id))
                   :body {:external_ids ["http://ex.tld/ctia/incident/incident-123"
                                         "http://ex.tld/ctia/incident/incident-456"]
                          :title "updated incident"
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
               {:external_ids ["http://ex.tld/ctia/incident/incident-123"
                               "http://ex.tld/ctia/incident/incident-456"]
                :type "incident"
                :id (id/long-id incident-id)
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
        (let [response (delete (str "ctia/incident/" (:short-id incident-id))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/incident/" (:id incident))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
