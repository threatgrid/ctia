(ns ctia.http.routes.campaign-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctia.test-helpers
             [search :refer [test-query-string-search]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-campaign-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/campaign"
    (let [{status :status
           campaign :parsed-body}
          (post "ctia/campaign"
                :body {:external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                                      "http://ex.tld/ctia/campaign/campaign-456"]
                       :title "campaign"
                       :description "description"
                       :tlp "green"
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

          campaign-id (id/long-id->id (:id campaign))
          campaign-external-ids (:external_ids campaign)]
      (is (= 201 status))
      (is (deep=
           {:id (id/long-id campaign-id)
            :type "campaign"
            :external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                           "http://ex.tld/ctia/campaign/campaign-456"]
            :title "campaign"
            :description "description"
            :tlp "green"
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
                   :created
                   :modified)))

      (testing "the campaign ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    campaign-id)      (:hostname    show-props)))
          (is (= (:protocol    campaign-id)      (:protocol    show-props)))
          (is (= (:port        campaign-id)      (:port        show-props)))
          (is (= (:path-prefix campaign-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/campaign/external_id"
        (let [response (get "ctia/campaign/external_id"
                            :headers {"api_key" "45c1f5e3f05d0"}
                            :query-params {"external_id" (rand-nth campaign-external-ids)})
              campaigns (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id campaign-id)
                 :type "campaign"
                 :external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                                "http://ex.tld/ctia/campaign/campaign-456"]
                 :title "campaign"
                 :description "description"
                 :tlp "green"
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
                 :owner "foouser"}]
               (map #(dissoc % :created :modified) campaigns)))))

      (test-query-string-search :campaign "description" :description)
      
      (testing "GET /ctia/campaign/:id"
        (let [response (get (str "ctia/campaign/" (:short-id campaign-id))
                            :headers {"api_key" "45c1f5e3f05d0"})
              campaign (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id campaign-id)
                :type "campaign"
                :external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                               "http://ex.tld/ctia/campaign/campaign-456"]
                :title "campaign"
                :description "description"
                :tlp "green"
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
                       :created
                       :modified)))))

      (testing "PUT /ctia/campaign/:id"
        (let [{status :status
               updated-campaign :parsed-body}
              (put (str "ctia/campaign/" (:short-id campaign-id))
                   :body {:title "modified campaign"
                          :external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                                         "http://ex.tld/ctia/campaign/campaign-456"]
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

              updated-campaign-id (id/long-id->id (:id updated-campaign))]
          (is (= 200 status))
          (is (deep=
               {:id (id/long-id updated-campaign-id)
                :external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                               "http://ex.tld/ctia/campaign/campaign-456"]
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
        (let [response (delete (str "ctia/campaign/" (:short-id campaign-id))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/campaign/" (:short-id campaign-id))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
