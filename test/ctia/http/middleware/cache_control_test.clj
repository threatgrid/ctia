(ns ctia.http.middleware.cache-control-test
  (:require [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [GET POST]]
             [es :as es-helpers]
             [fake-whoami-service :as whoami-helpers]]
            [ctim.domain.id :as id]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :once (join-fixtures [validate-schemas
                                    es-helpers/fixture-properties:es-store
                                    whoami-helpers/fixture-server
                                    helpers/fixture-ctia]))

(defn get-actor [app actor-id headers]
  (select-keys (GET app
                    (str "ctia/actor/" (:short-id actor-id))
                    :headers (merge headers {"Authorization" "45c1f5e3f05d0"}))
               [:status :headers :parsed-body]))

(deftest test-cache-control-middleware
  (testing "Cache control with ETags"
    (let [app (helpers/get-current-app)
          _ (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
          _ (whoami-helpers/set-whoami-response app
                                                "45c1f5e3f05d0"
                                                "foouser"
                                                "foogroup"
                                                "user")
          {status :status
           actor :parsed-body}
          (POST app
                "ctia/actor"
                :body {:title "actor"
                       :description "description"
                       :actor_type "Hacker"
                       :source "a source"
                       :confidence "High"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                    :end_time "2016-07-11T00:40:48.212-00:00"}}
                :headers {"Authorization" "45c1f5e3f05d0"})

          actor-id
          (id/long-id->id (:id actor))]

      (is (= 201 status))

      (let [first-res (get-actor app actor-id nil)
            etag (get-in first-res [:headers "ETag"])
            second-res (get-actor app actor-id {"If-none-match" etag})]
        (is (= 200 (:status first-res)))
        (is (not (nil? etag)))
        (is (= 304 (:status second-res)))
        (is (nil? (:parsed-body second-res)))
        (is (and (= (first etag) \")
                 (= (last etag) \"))
            "Etag is quoted")))))
