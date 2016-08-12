(ns ctia.http.middleware.cache-control-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.test-helpers
             [atom :as at-helpers]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    at-helpers/fixture-properties:atom-memory-store
                                    helpers/fixture-ctia
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn get-actor [id headers]
  (select-keys (get (str "ctia/actor/" id)
                    :headers (merge headers {"api_key" "45c1f5e3f05d0"})) [:status :headers :parsed-body]))

(deftest test-cache-control-middleware
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "Cache control with ETags"
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

      (let [first-res (get-actor (:id actor) nil)
            etag (get-in first-res [:headers "ETag"])
            second-res (get-actor (:id actor) {"If-none-match" etag})]

        (is (= 200 (:status first-res)))
        (is (not (nil? etag)))
        (is (= 304 (:status second-res)))
        (is (nil? (:parsed-body second-res))))))


  (testing "Cache control with If-Modified-Since"
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

      (let [first-res (get-actor (:id actor) nil)
            last-modified-since (get-in first-res [:headers "Last-Modified"])
            second-res (get-actor (:id actor) {"if-modified-since" last-modified-since})]

        (is (= 200 (:status first-res)))
        (is (not (nil? last-modified-since)))
        (is (= 304 (:status second-res)))
        (is (nil? (:parsed-body second-res)))))))
