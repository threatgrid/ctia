(ns ctia.http.routes.actor-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [search :refer [test-query-string-search]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-actor-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/actor"
    (let [{status :status
           actor :parsed-body}
          (post "ctia/actor"
                :body {:external_ids ["http://ex.tld/ctia/actor/actor-123"
                                      "http://ex.tld/ctia/actor/actor-456"]
                       :title "actor"
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

          actor-id
          (id/long-id->id (:id actor))

          actor-external-ids
          (:external_ids actor)]
      (is (= 201 status))
      (is (deep=
           {:external_ids ["http://ex.tld/ctia/actor/actor-123"
                           "http://ex.tld/ctia/actor/actor-456"]
            :type "actor"
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

      (testing "the actor ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    actor-id)      (:hostname    show-props)))
          (is (= (:protocol    actor-id)      (:protocol    show-props)))
          (is (= (:port        actor-id)      (:port        show-props)))
          (is (= (:path-prefix actor-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/actor/:id"
        (let [response (get (str "ctia/actor/" (:short-id actor-id))
                            :headers {"api_key" "45c1f5e3f05d0"})
              actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id actor-id)
                :external_ids ["http://ex.tld/ctia/actor/actor-123"
                               "http://ex.tld/ctia/actor/actor-456"]
                :type "actor"
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
                       :created
                       :modified)))))

      (test-query-string-search :actor "description" :description)
      
      (testing "GET /ctia/actor/external_id"
        (let [response (get "ctia/actor/external_id"
                            :headers {"api_key" "45c1f5e3f05d0"}
                            :query-params {"external_id" (rand-nth actor-external-ids)})
              actors (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id actor-id)
                 :external_ids ["http://ex.tld/ctia/actor/actor-123"
                                "http://ex.tld/ctia/actor/actor-456"]
                 :type "actor"
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
                 :tlp "green"}]
               (map #(dissoc % :created :modified) actors)))))

      (testing "PUT /ctia/actor/:id"
        (let [response (put (str "ctia/actor/" (:short-id actor-id))
                            :body {:external_ids ["http://ex.tld/ctia/actor/actor-123"
                                                  "http://ex.tld/ctia/actor/actor-456"]
                                   :title "modified actor"
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
               {:id (id/long-id actor-id)
                :external_ids ["http://ex.tld/ctia/actor/actor-123"
                               "http://ex.tld/ctia/actor/actor-456"]
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
        (let [response (delete (str "ctia/actor/" (:short-id actor-id))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/actor/" (:short-id actor-id))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
