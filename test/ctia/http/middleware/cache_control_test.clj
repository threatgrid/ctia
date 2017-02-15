(ns ctia.http.middleware.cache-control-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [es :as es-helpers]
             [fake-whoami-service :as whoami-helpers]]
            [ctim.domain.id :as id]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
                                    helpers/fixture-ctia
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn get-actor [actor-id headers]
  (select-keys (get (str "ctia/actor/" (:short-id actor-id))
                    :headers (merge headers {"api_key" "45c1f5e3f05d0"})) [:status :headers :parsed-body]))

(deftest test-cache-control-middleware
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "Cache control with ETags"
    (let [{status :status
           actor :parsed-body}
          (post "ctia/actor"
                :body {:title "actor"
                       :description "description"
                       :actor_type "Hacker"
                       :source "a source"
                       :confidence "High"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                    :end_time "2016-07-11T00:40:48.212-00:00"}}
                :headers {"api_key" "45c1f5e3f05d0"})

          actor-id
          (id/long-id->id (:id actor))]

      (is (= 201 status))

      (let [first-res (get-actor actor-id nil)
            etag (get-in first-res [:headers "ETag"])
            second-res (get-actor actor-id {"If-none-match" etag})]

        (is (= 200 (:status first-res)))
        (is (not (nil? etag)))
        (is (= 304 (:status second-res)))
        (is (nil? (:parsed-body second-res)))))))
