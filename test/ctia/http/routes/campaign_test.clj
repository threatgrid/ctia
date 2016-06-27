(ns ctia.http.routes.campaign-test
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-campaign-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/campaign"
    (let [response (post "ctia/campaign"
                         :body {:title "campaign"
                                :description "description"
                                :tlp "red"
                                :campaign_type "anything goes here"
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
      (is (= 201 (:status response)))
      (is (deep=
           {:type "campaign"
            :title "campaign"
            :description "description"
            :tlp "red"
            :schema_version schema-version
            :campaign_type "anything goes here"
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

      (testing "GET /ctia/campaign/:id"
        (let [response (get (str "ctia/campaign/" (:id campaign))
                            :headers {"api_key" "45c1f5e3f05d0"})
              campaign (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:type "campaign"
                :title "campaign"
                :description "description"
                :tlp "red"
                :schema_version schema-version
                :campaign_type "anything goes here"
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

      (testing "PUT /ctia/campaign/:id"
        (let [response (put (str "ctia/campaign/" (:id campaign))
                            :body {:title "modified campaign"
                                   :description "different description"
                                   :tlp "amber"
                                   :campaign_type "anything goes here"
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
                :type "campaign"
                :created (:created campaign)
                :title "modified campaign"
                :description "different description"
                :tlp "amber"
                :schema_version schema-version
                :campaign_type "anything goes here"
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

      (testing "DELETE /ctia/campaign/:id"
        (let [response (delete (str "ctia/campaign/" (:id campaign))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/campaign/" (:id campaign))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
