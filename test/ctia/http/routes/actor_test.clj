(ns ctia.http.routes.actor-test
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

(deftest-for-each-store test-actor-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/actor"
    (let [response (post "ctia/actor"
                         :body {:title "actor"
                                :description "description"
                                :actor_type "Hacker"
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
      (is (= 201 (:status response)))
      (is (deep=
           {:type "actor"
            :description "description",
            :actor_type "Hacker",
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
            :owner "foouser"
            :schema_version schema-version
            :tlp "green"}
           (dissoc actor
                   :id
                   :created
                   :modified)))

      (testing "GET /ctia/actor/:id"
        (let [response (get (str "ctia/actor/" (:id actor))
                            :headers {"api_key" "45c1f5e3f05d0"})
              actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:type "actor"
                :description "description",
                :actor_type "Hacker",
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
                :owner "foouser"
                :schema_version schema-version
                :tlp "green"}
               (dissoc actor
                       :id
                       :created
                       :modified)))))

      (testing "PUT /ctia/actor/:id"
        (let [response (put (str "ctia/actor/" (:id actor))
                            :body {:title "modified actor"
                                   :description "updated description"
                                   :actor_type "Hacktivist"
                                   :type "actor"
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
                :type "actor"
                :created (:created actor)
                :title "modified actor"
                :description "updated description"
                :actor_type "Hacktivist"
                :source "a source"
                :confidence "High"
                :associated_actors [{:actor_id "actor-789"}]
                :associated_campaigns [{:campaign_id "campaign-444"}
                                       {:campaign_id "campaign-555"}]
                :observed_TTPs [{:ttp_id "ttp-333"}
                                {:ttp_id  "ttp-999"}]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :owner "foouser"
                :schema_version schema-version
                :tlp "green"}
               (dissoc updated-actor
                       :modified)))))

      (testing "DELETE /ctia/actor/:id"
        (let [response (delete (str "ctia/actor/" (:id actor))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/actor/" (:id actor))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
