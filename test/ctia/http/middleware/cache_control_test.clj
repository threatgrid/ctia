(ns ctia.http.middleware.cache-control-test
  (:require
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.test-helpers.core :as helpers :refer [GET POST]]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
   [ctim.domain.id :as id]
   [schema.test :refer [validate-schemas]]))

(use-fixtures :once
  (join-fixtures
   [validate-schemas
    whoami-helpers/fixture-server]))


(def sample-actor
  {:title "actor"
   :description "description"
   :short_description "short description"
   :actor_types ["Hacker"]
   :source "a source"
   :confidence "High"
   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                :end_time "2016-07-11T00:40:48.212-00:00"}})

(defn get-actor [app actor-id headers]
  (select-keys (GET app
                    (str "ctia/actor/" (:short-id actor-id))
                    :headers (merge headers {"Authorization" "45c1f5e3f05d0"}))
               [:status :headers :parsed-body]))

(deftest test-cache-control-middleware
  (testing "cache control is disabled"
    (helpers/with-properties ["ctia.http.cache-control.enabled" false]
      (test-for-each-store-with-app
       (fn [app]
         (helpers/set-capabilities! app
                                    "foouser"
                                    ["foogroup"]
                                    "user"
                                    all-capabilities)
         (whoami-helpers/set-whoami-response app
                                             "45c1f5e3f05d0"
                                             "foouser"
                                             "foogroup"
                                             "user")
         (let [{status :status
                actor :parsed-body}
               (POST app
                     "ctia/actor"
                     :body sample-actor
                     :headers {"Authorization" "45c1f5e3f05d0"})

               actor-id
               (id/long-id->id (:id actor))]

           (is (= 201 status))
           (let [first-res (get-actor app actor-id nil)
                 etag (get-in first-res [:headers "ETag"])
                 second-res (get-actor app actor-id {"If-none-match" etag})]
             (is (= 200 (:status first-res)))
             (is (nil? etag))
             (is (= 200 (:status second-res)))
             (is (seq (:parsed-body second-res)))))))))

  (testing "cache control is enabled"
    (helpers/with-properties ["ctia.http.cache-control.enabled" true]
      (test-for-each-store-with-app
       (fn [app]
         (helpers/set-capabilities! app
                                    "foouser"
                                    ["foogroup"]
                                    "user"
                                    all-capabilities)
         (whoami-helpers/set-whoami-response app
                                             "45c1f5e3f05d0"
                                             "foouser"
                                             "foogroup"
                                             "user")
         (let [{status :status
                actor :parsed-body}
               (POST app
                     "ctia/actor"
                     :body sample-actor
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
                 "Etag is quoted"))))))))
