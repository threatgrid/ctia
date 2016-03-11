(ns cia.handler-test
  (:refer-clojure :exclude [get])
  (:require [cia.handler :as handler]
            [cia.test-helpers.core :refer [delete get post put] :as helpers]
            [cia.test-helpers.db :as db-helpers]
            [cia.test-helpers.index :as index-helpers]
            [cia.test-helpers.fake-whoami-service :as whoami-helpers]
            [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
            [cia.schemas.common :as c]))

(def all-capabilities
  #{:create-actor :read-actor :delete-actor
    :create-campaign :read-campaign :delete-campaign
    :create-exploit-target :read-exploit-target :delete-exploit-target
    :create-coa :read-coa :delete-coa
    :create-incident :read-incident :delete-incident
    :create-judgement :read-judgement :delete-judgement
    :create-judgement-indicator
    :create-feedback :read-feedback
    :create-sighting :read-sighting :delete-sighting
    :create-indicator :read-indicator
    :create-ttp :read-ttp :delete-ttp
    :list-judgements-by-observable
    :list-judgements-by-indicator
    :list-indicators-by-observable
    :list-sightings-by-observable
    :get-verdict})

(use-fixtures :once (join-fixtures [helpers/fixture-properties
                                    db-helpers/fixture-init-db
                                    whoami-helpers/fixture-server]))

(use-fixtures :each (join-fixtures [(helpers/fixture-server handler/app)
                                    helpers/fixture-schema-validation
                                    whoami-helpers/fixture-reset-state]))

(defmacro deftest-for-each-store [test-name & body]
  `(helpers/deftest-for-each-fixture ~test-name
     {:memory-store helpers/fixture-in-memory-store
      :sql-store    (join-fixtures [db-helpers/fixture-sql-store
                                    db-helpers/fixture-clean-db])

      :es-store     (join-fixtures [index-helpers/fixture-es-store
                                    index-helpers/fixture-clean-index])}


     ~@body))

(deftest-for-each-store test-version-routes
  (testing "we can request different content types"
    (let [response (get "cia/version" :accept :json)]
      (is (= "/cia" (get-in response [:parsed-body "base"]))))

    (let [response (get "cia/version" :accept :edn)]
      (is (= "/cia" (get-in response [:parsed-body :base]) ))))

  (testing "GET /cia/version"
    (let [response (get "cia/version")]
      (is (= 200 (:status response)))
      (is (= "0.1" (get-in response [:parsed-body :version]))))))

(deftest-for-each-store test-actor-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /cia/actor"
    (let [response (post "cia/actor"
                         :body {:title "actor"
                                :description "description"
                                :type "Hacker"
                                :source "a source"
                                :confidence "High"
                                :associated_actors [{:actor_id "actor-123"}
                                                    {:actor_id "actor-456"}]
                                :associated_campaigns [{:campaign_id "campaign-444"}
                                                       {:campaign_id "campaign-555"}]
                                :observed_TTPs [{:ttp_id "ttp-333"}
                                                {:ttp_id "ttp-999"}]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                             :end_time "2016-07-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          actor (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:description "description",
            :type "Hacker",
            :title "actor",
            :confidence "High",
            :source "a source"
            :associated_actors [{:actor_id "actor-123"}
                                {:actor_id "actor-456"}]
            :associated_campaigns [{:campaign_id "campaign-444"}
                                   {:campaign_id "campaign-555"}]
            :observed_TTPs [{:ttp_id "ttp-333"}
                            {:ttp_id "ttp-999"}]
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2016-07-11T00:40:48.212-00:00"}
            :owner "foouser"}
           (dissoc actor
                   :id
                   :created
                   :modified)))

      (testing "GET /cia/actor/:id"
        (let [response (get (str "cia/actor/" (:id actor))
                            :headers {"api_key" "45c1f5e3f05d0"})
              actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:description "description",
                :type "Hacker",
                :title "actor",
                :confidence "High",
                :source "a source"
                :associated_actors [{:actor_id "actor-123"}
                                    {:actor_id "actor-456"}]
                :associated_campaigns [{:campaign_id "campaign-444"}
                                       {:campaign_id "campaign-555"}]
                :observed_TTPs [{:ttp_id "ttp-333"}
                                {:ttp_id "ttp-999"}]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :owner "foouser"}
               (dissoc actor
                       :id
                       :created
                       :modified)))))

      (testing "PUT /cia/actor/:id"
        (let [response (put (str "cia/actor/" (:id actor))
                            :body {:title "modified actor"
                                   :description "updated description"
                                   :type "Hacktivist"
                                   :source "a source"
                                   :confidence "High"
                                   :associated_actors [{:actor_id "actor-789"}]
                                   :associated_campaigns [{:campaign_id "campaign-444"}
                                                          {:campaign_id "campaign-555"}]
                                   :observed_TTPs [{:ttp_id "ttp-333"}
                                                   {:ttp_id "ttp-999"}]
                                   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                                :end_time "2016-07-11T00:40:48.212-00:00"}}
                            :headers {"api_key" "45c1f5e3f05d0"})
              updated-actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (:id actor)
                :created (:created actor)
                :title "modified actor"
                :description "updated description"
                :type "Hacktivist"
                :source "a source"
                :confidence "High"
                :associated_actors [{:actor_id "actor-789"}]
                :associated_campaigns [{:campaign_id "campaign-444"}
                                       {:campaign_id "campaign-555"}]
                :observed_TTPs [{:ttp_id "ttp-333"}
                                {:ttp_id  "ttp-999"}]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :owner "foouser"}
               (dissoc updated-actor
                       :modified)))))

      (testing "DELETE /cia/actor/:id"
        (let [response (delete (str "cia/actor/" (:id actor))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/actor/" (:id actor))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))

(deftest-for-each-store test-campaign-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /cia/campaign"
    (let [response (post "cia/campaign"
                         :body {:title "campaign"
                                :description "description"
                                :type "anything goes here"
                                :intended_effect ["Theft"]
                                :indicators [{:indicator_id "indicator-foo"}
                                             {:indicator_id "indicator-bar"}]
                                :attribution [{:confidence "High"
                                               :source "source"
                                               :relationship "relationship"
                                               :actor_id "actor-123"}]
                                :related_incidents [{:confidence "High"
                                                     :source "source"
                                                     :relationship "relationship"
                                                     :incident_id "incident-222"}]
                                :related_TTPs [{:confidence "High"
                                                :source "source"
                                                :relationship "relationship"
                                                :ttp_id "ttp-999"}]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                             :end_time "2016-07-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          campaign (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:title "campaign"
            :description "description"
            :type "anything goes here"
            :intended_effect ["Theft"]
            :indicators [{:indicator_id "indicator-foo"}
                         {:indicator_id "indicator-bar"}]
            :attribution [{:confidence "High"
                           :source "source"
                           :relationship "relationship"
                           :actor_id "actor-123"}]
            :related_incidents [{:confidence "High"
                                 :source "source"
                                 :relationship "relationship"
                                 :incident_id "incident-222"}]
            :related_TTPs [{:confidence "High"
                            :source "source"
                            :relationship "relationship"
                            :ttp_id "ttp-999"}]
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2016-07-11T00:40:48.212-00:00"}
            :owner "foouser"}
           (dissoc campaign
                   :id
                   :created
                   :modified)))

      (testing "GET /cia/campaign/:id"
        (let [response (get (str "cia/campaign/" (:id campaign))
                            :headers {"api_key" "45c1f5e3f05d0"})
              campaign (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:title "campaign"
                :description "description"
                :type "anything goes here"
                :intended_effect ["Theft"]
                :indicators [{:indicator_id "indicator-foo"}
                             {:indicator_id "indicator-bar"}]
                :attribution [{:confidence "High"
                               :source "source"
                               :relationship "relationship"
                               :actor_id "actor-123"}]
                :related_incidents [{:confidence "High"
                                     :source "source"
                                     :relationship "relationship"
                                     :incident_id "incident-222"}]
                :related_TTPs [{:confidence "High"
                                :source "source"
                                :relationship "relationship"
                                :ttp_id "ttp-999"}]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :owner "foouser"}
               (dissoc campaign
                       :id
                       :created
                       :modified)))))

      (testing "PUT /cia/campaign/:id"
        (let [response (put (str "cia/campaign/" (:id campaign))
                            :body {:title "modified campaign"
                                   :description "different description"
                                   :type "anything goes here"
                                   :intended_effect ["Brand Damage"]
                                   :indicators [{:indicator_id "indicator-foo"}
                                                {:indicator_id "indicator-bar"}]
                                   :attribution [{:confidence "High"
                                                  :source "source"
                                                  :relationship "relationship"
                                                  :actor_id "actor-123"}]
                                   :related_incidents [{:confidence "High"
                                                        :source "source"
                                                        :relationship "relationship"
                                                        :incident_id "incident-222"}]
                                   :related_TTPs [{:confidence "High"
                                                   :source "source"
                                                   :relationship "relationship"
                                                   :ttp_id "ttp-999"}]
                                   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                                :end_time "2016-07-11T00:40:48.212-00:00"}}
                            :headers {"api_key" "45c1f5e3f05d0"})
              updated-campaign (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (:id campaign)
                :created (:created campaign)
                :title "modified campaign"
                :description "different description"
                :type "anything goes here"
                :intended_effect ["Brand Damage"]
                :indicators [{:indicator_id "indicator-foo"}
                             {:indicator_id "indicator-bar"}]
                :attribution [{:confidence "High"
                               :source "source"
                               :relationship "relationship"
                               :actor_id "actor-123"}]
                :related_incidents [{:confidence "High"
                                     :source "source"
                                     :relationship "relationship"
                                     :incident_id "incident-222"}]
                :related_TTPs [{:confidence "High"
                                :source "source"
                                :relationship "relationship"
                                :ttp_id "ttp-999"}]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :owner "foouser"}
               (dissoc updated-campaign
                       :modified)))))

      (testing "DELETE /cia/campaign/:id"
        (let [response (delete (str "cia/campaign/" (:id campaign))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/campaign/" (:id campaign))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))

(deftest-for-each-store test-coa-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /cia/coa"
    (let [response (post "cia/coa"
                         :body {:title "coa"
                                :description "description"
                                :type "Eradication"
                                :objective ["foo" "bar"]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          coa (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:title "coa"
            :description "description"
            :type "Eradication"
            :objective ["foo" "bar"]
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc coa
                   :id
                   :created
                   :modified)))

      (testing "GET /cia/coa/:id"
        (let [response (get (str "cia/coa/" (:id coa))
                            :headers {"api_key" "45c1f5e3f05d0"})
              coa (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:title "coa"
                :description "description"
                :type "Eradication"
                :objective ["foo" "bar"]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :owner "foouser"}
               (dissoc coa
                       :id
                       :created
                       :modified)))))

      (testing "PUT /cia/coa/:id"
        (let [{updated-coa :parsed-body
               status :status}
              (put (str "cia/coa/" (:id coa))
                   :body {:title "updated coa"
                          :description "updated description"
                          :type "Hardening"
                          :objective ["foo" "bar"]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:id (:id coa)
                :created (:created coa)
                :title "updated coa"
                :description "updated description"
                :type "Hardening"
                :objective ["foo" "bar"]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :owner "foouser"}
               (dissoc updated-coa
                       :modified)))))

      (testing "DELETE /cia/coa/:id"
        (let [response (delete (str "/cia/coa/" (:id coa))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "/cia/coa/" (:id coa))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))

(deftest-for-each-store test-exploit-target-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /cia/exploit-target"
    (let [response (post "cia/exploit-target"
                         :body {:title "exploit-target"
                                :description "description"
                                :vulnerability [{:title "vulnerability"
                                                 :description "description"}]
                                :potential_COAs [{:COA_id "coa-777"}
                                                 {:COA_id "coa-333"}]
                                :related_exploit_targets [{:confidence "High"
                                                           :source "source"
                                                           :relationship "relationship"
                                                           :exploit_target_id "exploit-target-123"}]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          exploit-target (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:title "exploit-target"
            :description "description"
            :vulnerability [{:title "vulnerability"
                             :description "description"}]
            :potential_COAs [{:COA_id "coa-777"}
                             {:COA_id "coa-333"}]
            :related_exploit_targets [{:confidence "High"
                                       :source "source"
                                       :relationship "relationship"
                                       :exploit_target_id "exploit-target-123"}]
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc exploit-target
                   :id
                   :created
                   :modified)))

      (testing "GET /cia/exploit-target/:id"
        (let [response (get (str "cia/exploit-target/" (:id exploit-target))
                            :headers {"api_key" "45c1f5e3f05d0"})
              exploit-target (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:title "exploit-target"
                :description "description"
                :vulnerability [{:title "vulnerability"
                                 :description "description"}]
                :potential_COAs [{:COA_id "coa-777"}
                                 {:COA_id "coa-333"}]
                :related_exploit_targets [{:confidence "High"
                                           :source "source"
                                           :relationship "relationship"
                                           :exploit_target_id "exploit-target-123"}]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :owner "foouser"}
               (dissoc exploit-target
                       :id
                       :created
                       :modified)))))

      (testing "PUT /cia/exploit-target/:id"
        (let [{updated-exploit-target :parsed-body
               status :status}
              (put (str "cia/exploit-target/" (:id exploit-target))
                   :body {:title "updated exploit-target"
                          :description "updated description"
                          :vulnerability [{:title "vulnerability"
                                           :description "description"}]
                          :potential_COAs [{:COA_id "coa-777"}
                                           {:COA_id "coa-333"}]
                          :related_exploit_targets [{:confidence "Medium"
                                                     :source "source"
                                                     :relationship "another relationship"
                                                     :exploit_target_id "exploit-target-123"}]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:id (:id exploit-target)
                :title "updated exploit-target"
                :description "updated description"
                :vulnerability [{:title "vulnerability"
                                 :description "description"}]
                :potential_COAs [{:COA_id "coa-777"}
                                 {:COA_id "coa-333"}]
                :related_exploit_targets [{:confidence "Medium"
                                           :source "source"
                                           :relationship "another relationship"
                                           :exploit_target_id "exploit-target-123"}]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :owner "foouser"
                :created (:created exploit-target)}
               (dissoc updated-exploit-target
                       :modified)))))

      (testing "DELETE /cia/exploit-target/:id"
        (let [response (delete (str "cia/exploit-target/" (:id exploit-target))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/exploit-target/" (:id exploit-target))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))

(deftest-for-each-store test-incident-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /cia/incident"
    (let [response (post "cia/incident"
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
      (is (= 200 (:status response)))
      (is (deep=
           {:title "incident"
            :description "description"
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

      (testing "GET /cia/incident/:id"
        (let [response (get (str "cia/incident/" (:id incident))
                            :headers {"api_key" "45c1f5e3f05d0"})
              incident (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:title "incident"
                :description "description"
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

      (testing "PUT /cia/incident/:id"
        (let [{status :status
               updated-incident :parsed-body}
              (put (str "cia/incident/" (:id incident))
                   :body {:title "updated incident"
                          :description "updated description"
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
               {:id (:id incident)
                :created (:created incident)
                :title "updated incident"
                :description "updated description"
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

      (testing "DELETE /cia/incident/:id"
        (let [response (delete (str "cia/incident/" (:id incident))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/incident/" (:id incident))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))

(deftest-for-each-store test-indicator-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /cia/indicator"
    (let [response (post "cia/indicator"
                         :body {:title "indicator"
                                :description "description"
                                :producer "producer"
                                :type ["C2" "IP Watchlist"]
                                :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                             :end_time "2016-07-11T00:40:48.212-00:00"}
                                :related_campaigns [{:confidence "High"
                                                     :source "source"
                                                     :relationship "relationship"
                                                     :campaign_id "campaign-123"}]
                                :related_COAs [{:confidence "High"
                                                :source "source"
                                                :relationship "relationship"
                                                :COA_id "coa-123"}]
                                :judgements [{:judgement_id "judgement-123"}
                                             {:judgement_id "judgement-234"}]}
                         :headers {"api_key" "45c1f5e3f05d0"})
          indicator (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:title "indicator"
            :description "description"
            :producer "producer"
            :type ["C2" "IP Watchlist"]
            :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                         :end_time #inst "2016-07-11T00:40:48.212-00:00"}
            :related_campaigns [{:confidence "High"
                                 :source "source"
                                 :relationship "relationship"
                                 :campaign_id "campaign-123"}]
            :related_COAs [{:confidence "High"
                            :source "source"
                            :relationship "relationship"
                            :COA_id "coa-123"}]
            :judgements [{:judgement_id "judgement-123"}
                         {:judgement_id "judgement-234"}]
            :owner "foouser"}
           (dissoc indicator
                   :id
                   :created
                   :modified)))

      (testing "GET /cia/indicator/:id"
        (let [response (get (str "cia/indicator/" (:id indicator))
                            :headers {"api_key" "45c1f5e3f05d0"})
              indicator (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:title "indicator"
                :description "description"
                :producer "producer"
                :type ["C2" "IP Watchlist"]
                :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :related_campaigns [{:confidence "High"
                                     :source "source"
                                     :relationship "relationship"
                                     :campaign_id "campaign-123"}]
                :related_COAs [{:confidence "High"
                                :source "source"
                                :relationship "relationship"
                                :COA_id "coa-123"}]
                :judgements [{:judgement_id "judgement-123"}
                             {:judgement_id "judgement-234"}]
                :owner "foouser"}
               (dissoc indicator
                       :id
                       :created
                       :modified)))))

      (testing "PUT /cia/indicator/:id"
        (let [{status :status
               updated-indicator :parsed-body}
              (put (str "cia/indicator/" (:id indicator))
                   :body {:title "updated indicator"
                          :description "updated description"
                          :producer "producer"
                          :type ["IP Watchlist"]
                          :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}
                          :related_campaigns [{:confidence "Low"
                                               :source "source"
                                               :relationship "relationship"
                                               :campaign_id "campaign-123"}]
                          :related_COAs [{:confidence "High"
                                          :source "source"
                                          :relationship "relationship"
                                          :COA_id "coa-123"}]
                          :judgements [{:judgement_id "judgement-123"}
                                       {:judgement_id "judgement-234"}]}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:id (:id indicator)
                :created (:created indicator)
                :title "updated indicator"
                :description "updated description"
                :producer "producer"
                :type ["IP Watchlist"]
                :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :related_campaigns [{:confidence "Low"
                                     :source "source"
                                     :relationship "relationship"
                                     :campaign_id "campaign-123"}]
                :related_COAs [{:confidence "High"
                                :source "source"
                                :relationship "relationship"
                                :COA_id "coa-123"}]
                :judgements [{:judgement_id "judgement-123"}
                             {:judgement_id "judgement-234"}]
                :owner "foouser"}
               (dissoc updated-indicator
                       :modified)))))

      (testing "DELETE /cia/indicator/:id"
        (let [response (delete (str "cia/indicator/" (:id indicator))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          ;; Deleting indicators is not allowed
          (is (= 404 (:status response))))))))

(deftest-for-each-store test-sighting-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /cia/sighting"
    (let [{status :status
           sighting :parsed-body
           :as response}
          (post "cia/sighting"
                :body {:timestamp "2016-05-11T00:40:48.212-00:00"
                       :source "source"
                       :reference "http://example.com/123"
                       :confidence "High"
                       :description "description"
                       :related_judgements [{:judgement_id "judgement-123"}
                                            {:judgement_id "judgement-234"}]}
                :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 200 status))
      (is (deep=
           {:timestamp #inst "2016-05-11T00:40:48.212-00:00"
            :source "source"
            :reference "http://example.com/123"
            :confidence "High"
            :description "description"
            :related_judgements [{:judgement_id "judgement-123"}
                                 {:judgement_id "judgement-234"}]
            :owner "foouser"}
           (dissoc sighting
                   :id
                   :created
                   :modified)))

      (testing "GET /cia/sighting/:id"
        (let [{status :status
               sighting :parsed-body}
              (get (str "cia/sighting/" (:id sighting))
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:timestamp #inst "2016-05-11T00:40:48.212-00:00"
                :source "source"
                :reference "http://example.com/123"
                :confidence "High"
                :description "description"
                :related_judgements [{:judgement_id "judgement-123"}
                                     {:judgement_id "judgement-234"}]
                :owner "foouser"}
               (dissoc sighting
                       :id
                       :created
                       :modified)))))

      (testing "PUT /cia/sighting/:id"
        (let [{status :status
               updated-sighting :parsed-body}
              (put (str "cia/sighting/" (:id sighting))
                   :body {:timestamp "2016-05-11T00:40:48.212-00:00"
                          :source "updated source"
                          :reference "http://example.com/123"
                          :confidence "Medium"
                          :description "updated description"
                          :related_judgements [{:judgement_id "judgement-123"}
                                               {:judgement_id "judgement-234"}]}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:timestamp #inst "2016-05-11T00:40:48.212-00:00"
                :source "updated source"
                :reference "http://example.com/123"
                :confidence "Medium"
                :description "updated description"
                :related_judgements [{:judgement_id "judgement-123"}
                                     {:judgement_id "judgement-234"}]
                :owner "foouser"}
               (dissoc updated-sighting
                       :id
                       :created
                       :modified)))))

      (testing "DELETE /cia/sighting/:id"
        (let [{status :status} (delete (str "cia/sighting/" (:id sighting))
                                       :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 status))
          (let [{status :status} (get (str "cia/sighting/" (:id sighting))
                                      :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 status))))))))

(deftest-for-each-store test-judgement-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (helpers/set-capabilities! "baruser" "user" #{})
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")
  (whoami-helpers/set-whoami-response "2222222222222" "baruser" "user")

  (testing "POST /cia/judgement"
    (let [response (post "cia/judgement"
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
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :priority 100
            :severity 100
            :confidence "Low"
            :source "test"
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

      (testing "GET /cia/judgement/:id"
        (let [response (get (str "cia/judgement/" (:id judgement))
                            :headers {"api_key" "45c1f5e3f05d0"})
              judgement (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:observable {:value "1.2.3.4"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :priority 100
                :severity 100
                :confidence "Low"
                :source "test"
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

      (testing "GET /cia/judgement/:id with query-param api_key"
        (let [{status :status
               judgement :parsed-body
               :as response}
              (get (str "cia/judgement/" (:id judgement))
                   :query-params {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 (:status response)))
          (is (deep=
               {:observable {:value "1.2.3.4"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :priority 100
                :severity 100
                :confidence "Low"
                :source "test"
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

      (testing "GET /cia/judgement/:id authentication failures"
        (testing "no api_key"
          (let [{body :parsed-body status :status}
                (get (str "cia/judgement/" (:id judgement)))]
            (is (= 403 status))
            (is (= {:message "Only authenticated users allowed"} body))))

        (testing "unknown api_key"
          (let [{body :parsed-body status :status}
                (get (str "cia/judgement/" (:id judgement))
                     :headers {"api_key" "1111111111111"})]
            (is (= 403 status))
            (is (= {:message "Only authenticated users allowed"} body))))

        (testing "doesn't have read capability"
          (let [{body :parsed-body status :status}
                (get (str "cia/judgement/" (:id judgement))
                     :headers {"api_key" "2222222222222"})]
            (is (= 401 status))
            (is (= {:message "Missing capability",
                    :capabilities #{:admin :read-judgement},
                    :owner "baruser"}
                   body)))))

      (testing "DELETE /cia/judgement/:id"
        (let [temp-judgement (-> (post "cia/judgement"
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
              response (delete (str "cia/judgement/" (:id temp-judgement))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/judgement/" (:id temp-judgement))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))

      (testing "POST /cia/judgement/:id/feedback"
        (let [response (post (str "cia/judgement/" (:id judgement) "/feedback")
                             :body {:feedback -1
                                    :reason "false positive"}
                             :headers {"api_key" "45c1f5e3f05d0"})
              feedback (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:judgement (:id judgement),
                :feedback -1,
                :reason "false positive"
                :owner "foouser"}
               (dissoc feedback
                       :id
                       :created))))

        (testing "GET /cia/judgement/:id/feedback"
          ;; create some more feedbacks
          (let [response (post "cia/judgement"
                               :body {:indicators ["indicator-222"]
                                      :observable {:value "4.5.6.7"
                                                   :type "ip"}
                                      :disposition 1
                                      :source "test"}
                               :headers {"api_key" "45c1f5e3f05d0"})
                another-judgement (:parsed-body response)]
            (post (str "cia/judgement/" (:id another-judgement) "/feedback")
                  :body {:feedback 0
                         :reason "yolo"}
                  :headers {"api_key" "45c1f5e3f05d0"}))
          (post (str "cia/judgement/" (:id judgement) "/feedback")
                :body {:feedback 1
                       :reason "true positive"}
                :headers {"api_key" "45c1f5e3f05d0"})

          (let [response (get (str "cia/judgement/" (:id judgement) "/feedback")
                              :headers {"api_key" "45c1f5e3f05d0"})
                feedbacks (:parsed-body response)]
            (is (= 200 (:status response)))
            (is (deep=
                 #{{:judgement (:id judgement),
                    :feedback -1,
                    :reason "false positive"
                    :owner "foouser"}
                   {:judgement (:id judgement),
                    :feedback 1,
                    :reason "true positive"
                    :owner "foouser"}}
                 (set (map #(dissoc % :id :created)
                           feedbacks))))))))))

(deftest-for-each-store test-judgement-routes-for-dispositon-determination
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST a judgement with dispositon (id)"
    (let [response (post "cia/judgement"
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
      (is (= 200 (:status response)))
      (is (deep=
           {:observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :source "test"
            :priority 100
            :severity 100
            :confidence "Low"
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc judgement
                   :id
                   :created)))))

  (testing "POST a judgement with disposition_name"
    (let [response (post "cia/judgement"
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
      (is (= 200 (:status response)))
      (is (deep=
           {:observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :source "test"
            :priority 100
            :severity 100
            :confidence "Low"
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc judgement
                   :id
                   :created)))))

  (testing "POST a judgement without disposition"
    (let [response (post "cia/judgement"
                         :body {:observable {:value "1.2.3.4"
                                             :type "ip"}
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 5
            :disposition_name "Unknown"
            :source "test"
            :priority 100
            :severity 100
            :confidence "Low"
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc judgement
                   :id
                   :created)))))

  (testing "POST a judgement with mismatching disposition/disposition_name"
    (let [response (post "cia/judgement"
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

(deftest-for-each-store test-get-things-by-observable-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (let [{{judgement-1-id :id} :parsed-body
         judgement-1-status :status}
        (post "cia/judgement"
              :body {:observable {:value "1.2.3.4"
                                  :type "ip"}
                     :disposition 2
                     :source "judgement 1"
                     :priority 100
                     :severity 100
                     :confidence "Low"
                     :indicators []
                     :valid_time {:start_time "2016-02-01T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {sighting-1-status :status
         {sighting-1-id :id} :parsed-body}
        (post "cia/sighting"
              :body {:timestamp "2016-02-01T00:00:00.000-00:00"
                     :source "foo"
                     :confidence "Medium"
                     :description "sighting 1"}
              :headers {"api_key" "45c1f5e3f05d0"})

        {sighting-2-status :status
         {sighting-2-id :id} :parsed-body}
        (post "cia/sighting"
              :body {:timestamp "2016-02-01T12:00:00.000-00:00"
                     :source "bar"
                     :confidence "High"
                     :description "sighting 2"}
              :headers {"api_key" "45c1f5e3f05d0"})

        {indicator-1-status :status
         {indicator-1-id :id} :parsed-body}
        (post "cia/indicator"
              :body {:title "indicator"
                     :judgements [{:judgement_id judgement-1-id}]
                     :sightings [{:sighting_id sighting-1-id}
                                 {:sighting_id sighting-2-id}]
                     :description "indicator 1"
                     :producer "producer"
                     :type ["C2" "IP Watchlist"]
                     :valid_time {:end_time "2016-02-12T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {judgement-1-update-status :status}
        (post (str "cia/judgement/" judgement-1-id "/indicator")
              :body {:indicator_id indicator-1-id}
              :headers {"api_key" "45c1f5e3f05d0"})

        {{judgement-2-id :id} :parsed-body
         judgement-2-status :status}
        (post "cia/judgement"
              :body {:observable {:value "10.0.0.1"
                                  :type "ip"}
                     :disposition 2
                     :source "judgement 2"
                     :priority 100
                     :severity 100
                     :confidence "High"
                     :indicators []
                     :valid_time {:start_time "2016-02-01T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {sighting-3-status :status
         {sighting-3-id :id} :parsed-body}
        (post "cia/sighting"
              :body {:timestamp "2016-02-04T12:00:00.000-00:00"
                     :source "spam"
                     :confidence "None"
                     :description "sighting 3"}
              :headers {"api_key" "45c1f5e3f05d0"})

        {indicator-2-status :status
         {indicator-2-id :id} :parsed-body}
        (post "cia/indicator"
              :body {:title "indicator"
                     :judgements [{:judgement_id judgement-2-id}]
                     :sightings [{:sighting_id sighting-3-id}]
                     :description "indicator 2"
                     :producer "producer"
                     :type ["C2" "IP Watchlist"]
                     :valid_time {:start_time "2016-01-12T00:00:00.000-00:00"
                                  :end_time "2016-02-12T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {judgement-2-update-status :status}
        (post (str "cia/judgement/" judgement-2-id "/indicator")
              :body {:indicator_id indicator-2-id}
              :headers {"api_key" "45c1f5e3f05d0"})

        {{judgement-3-id :id} :parsed-body
         judgement-3-status :status}
        (post "cia/judgement"
              :body {:observable {:value "10.0.0.1"
                                  :type "ip"}
                     :disposition 2
                     :source "judgement 3"
                     :priority 100
                     :severity 100
                     :confidence "Low"
                     :indicators []
                     :valid_time {:start_time "2016-02-01T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {sighting-4-status :status
         {sighting-4-id :id} :parsed-body}
        (post "cia/sighting"
              :body {:timestamp "2016-02-05T01:00:00.000-00:00"
                     :source "foo"
                     :confidence "High"
                     :description "sighting 4"}
              :headers {"api_key" "45c1f5e3f05d0"})

        {sighting-5-status :status
         {sighting-5-id :id} :parsed-body}
        (post "cia/sighting"
              :body {:timestamp "2016-02-05T02:00:00.000-00:00"
                     :source "bar"
                     :confidence "Low"
                     :description "sighting 5"}
              :headers {"api_key" "45c1f5e3f05d0"})

        {indicator-3-status :status
         {indicator-3-id :id} :parsed-body}
        (post "cia/indicator"
              :body {:title "indicator"
                     :judgements [{:judgement_id judgement-3-id
                                   :confidence "High"}]
                     :sightings [{:sighting_id sighting-4-id}
                                 {:sighting_id sighting-5-id}]
                     :description "indicator 3"
                     :producer "producer"
                     :type ["C2" "IP Watchlist"]
                     :valid_time {:start_time "2016-01-11T00:00:00.000-00:00"
                                  :end_time "2016-02-11T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {judgement-3-update-status :status}
        (post (str "cia/judgement/" judgement-3-id "/indicator")
              :body {:indicator_id indicator-3-id}
             :headers {"api_key" "45c1f5e3f05d0"})]

    (testing "With successful test setup"
      (is (= 200 judgement-1-status))
      (is (= 200 sighting-1-status))
      (is (= 200 sighting-2-status))
      (is (= 200 indicator-1-status))
      (is (= 200 judgement-1-update-status))
      (is (= 200 judgement-2-status))
      (is (= 200 sighting-3-status))
      (is (= 200 indicator-2-status))
      (is (= 200 judgement-2-update-status))
      (is (= 200 judgement-3-status))
      (is (= 200 sighting-4-status))
      (is (= 200 sighting-5-status))
      (is (= 200 indicator-3-status))
      (is (= 200 judgement-3-update-status)))

    (testing "GET /cia/:observable_type/:observable_value/judgements"
      (let [{status :status
             judgements :parsed-body}
            (get "cia/ip/10.0.0.1/judgements"
                 :headers {"api_key" "45c1f5e3f05d0"})]
        (is (= 200 status))
        (is (deep=
             #{{:id judgement-2-id
                :observable {:value "10.0.0.1"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :source "judgement 2"
                :priority 100
                :severity 100
                :confidence "High"
                :indicators [{:indicator_id indicator-2-id}]
                :valid_time {:start_time #inst "2016-02-01T00:00:00.000-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :owner "foouser"}
               {:id judgement-3-id
                :observable {:value "10.0.0.1"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :source "judgement 3"
                :priority 100
                :severity 100
                :confidence "Low"
                :indicators [{:indicator_id indicator-3-id}]
                :valid_time {:start_time #inst "2016-02-01T00:00:00.000-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :owner "foouser"}}
             (->> judgements
                  (map #(dissoc % :created))
                  set)))))

    (testing "GET /cia/:observable_type/:observable_value/indicators"
      (let [response (get "cia/ip/10.0.0.1/indicators"
                          :headers {"api_key" "45c1f5e3f05d0"})
            indicators (:parsed-body response)]
        (is (= 200 (:status response)))
        (is (deep=
             #{{:id indicator-2-id
                :title "indicator"
                :judgements [{:judgement_id judgement-2-id}]
                :sightings [{:sighting_id sighting-3-id}]
                :description "indicator 2"
                :producer "producer"
                :type ["C2" "IP Watchlist"]
                :valid_time {:start_time #inst "2016-01-12T00:00:00.000-00:00"
                             :end_time #inst "2016-02-12T00:00:00.000-00:00"}
                :owner "foouser"}
               {:id indicator-3-id
                :title "indicator"
                :judgements [{:judgement_id judgement-3-id
                              :confidence "High"}]
                :sightings [{:sighting_id sighting-4-id}
                            {:sighting_id sighting-5-id}]
                :description "indicator 3"
                :producer "producer"
                :type ["C2" "IP Watchlist"]
                :valid_time {:start_time #inst "2016-01-11T00:00:00.000-00:00"
                             :end_time #inst "2016-02-11T00:00:00.000-00:00"}
                :owner "foouser"}}
             (->> indicators
                  (map #(dissoc % :created :modified))
                  set)))))

    (testing "GET /cia/:observable_type/:observable_value/sightings"
      (let [{status :status
             sightings :parsed-body
             :as response}
            (get "cia/ip/10.0.0.1/sightings"
                 :headers {"api_key" "45c1f5e3f05d0"})]
        (is (= 200 status))
        (is (deep=
             #{{:id sighting-3-id
                :timestamp #inst "2016-02-04T12:00:00.000-00:00"
                :source "spam"
                :confidence "None"
                :description "sighting 3"
                :owner "foouser"}
               {:id sighting-4-id
                :timestamp #inst "2016-02-05T01:00:00.000-00:00"
                :source "foo"
                :confidence "High"
                :description "sighting 4"
                :owner "foouser"}
               {:id sighting-5-id
                :timestamp #inst "2016-02-05T02:00:00.000-00:00"
                :source "bar"
                :confidence "Low"
                :description "sighting 5"
                :owner "foouser"}}
             (->> sightings
                  (map #(dissoc % :created :modified))
                  set)))))))

(deftest-for-each-store test-observable-verdict-route
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "test setup: create a judgement (1)"
    ;; Incorrect observable
    (let [response (post "cia/judgement"
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
      (is (= 200 (:status response)))))

  (testing "test setup: create a judgement (2)"
    ;; Lower priority
    (let [response (post "cia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 1
                                :source "test"
                                :priority 90
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 200 (:status response)))))

  (testing "test setup: create a judgement (3)"
    ;; Wrong disposition
    (let [response (post "cia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 3
                                :source "test"
                                :priority 99
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 200 (:status response)))))

  (testing "test setup: create a judgement (4)"
    ;; Loses a tie because of its timestamp being later
    (let [response (post "cia/judgement"
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
      (is (= 200 (:status response)))))

  (testing "with a highest-priority judgement"
    (let [response (post "cia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 99
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement-1 (:parsed-body response)]
      (is (= 200 (:status response))) ;; success creating judgement

      (testing "GET /cia/:observable_type/:observable_value/verdict"
        (let [response (get "cia/ip/10.0.0.1/verdict"
                            :headers {"api_key" "45c1f5e3f05d0"})
              verdict (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:disposition 2
                  :disposition_name "Malicious"
                  :judgement_id (:id judgement-1)}
                 verdict)))))))

(deftest-for-each-store test-observable-verdict-route-2
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  ;; This test case catches a bug that was in the in-memory store
  ;; It tests the code path where priority is equal but dispositions differ
  (testing "test setup: create a judgement (1)"
    (let [response (post "cia/judgement"
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
      (is (= 200 (:status response)))))
  (testing "with a verdict judgement"
    (let [response (post "cia/judgement"
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
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))

      (testing "GET /cia/:observable_type/:observable_value/verdict"
        (with-redefs [clj-time.core/now (constantly (c/timestamp "2016-02-12T15:42:58.232-00:00"))]
          (let [response (get "cia/ip/10.0.0.1/verdict"
                              :headers {"api_key" "45c1f5e3f05d0"})
                verdict (:parsed-body response)]
            (is (= 200 (:status response)))
            (is (= {:disposition 2
                    :disposition_name "Malicious"
                    :judgement_id (:id judgement)}
                   verdict))))))))

(deftest-for-each-store test-ttp-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /cia/ttp"
    (let [response (post "cia/ttp"
                         :body {:title "ttp"
                                :description "description"
                                :type "foo"
                                :indicators ["indicator-1" "indicator-2"]
                                :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                                  {:exploit_target_id "exploit-target-234"}]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                             :end_time "2016-07-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          ttp (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:title "ttp"
            :description "description"
            :type "foo"
            :indicators ["indicator-1" "indicator-2"]
            :exploit_targets [{:exploit_target_id "exploit-target-123"}
                              {:exploit_target_id "exploit-target-234"}]
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2016-07-11T00:40:48.212-00:00"}
            :owner "foouser"}
           (dissoc ttp
                   :id
                   :created
                   :modified)))

      (testing "GET /cia/ttp/:id"
        (let [response (get (str "cia/ttp/" (:id ttp))
                            :headers {"api_key" "45c1f5e3f05d0"})
              ttp (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:title "ttp"
                :description "description"
                :type "foo"
                :indicators ["indicator-1" "indicator-2"]
                :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                  {:exploit_target_id "exploit-target-234"}]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :owner "foouser"}
               (dissoc ttp
                       :id
                       :created
                       :modified)))))

      (testing "PUT /cia/ttp/:id"
        (let [{status :status
               updated-ttp :parsed-body}
              (put (str "cia/ttp/" (:id ttp))
                   :body {:title "updated ttp"
                          :description "updated description"
                          :type "bar"
                          :indicators ["indicator-1" "indicator-2"]
                          :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                            {:exploit_target_id "exploit-target-234"}]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:id (:id ttp)
                :created (:created ttp)
                :title "updated ttp"
                :description "updated description"
                :type "bar"
                :indicators ["indicator-1" "indicator-2"]
                :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                  {:exploit_target_id "exploit-target-234"}]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :owner "foouser"}
               (dissoc updated-ttp
                       :modified)))))

      (testing "DELETE /cia/ttp/:id"
        (let [response (delete (str "cia/ttp/" (:id ttp))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/ttp/" (:id ttp))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
