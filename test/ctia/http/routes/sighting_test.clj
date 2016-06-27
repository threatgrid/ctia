(ns ctia.http.routes.sighting-test
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [http :refer [api-key]]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-sighting-route-with-invalid-ID-post
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "POST /ctia/sighting with invalid ID"
    (let [{status :status}
          (post "ctia/sighting"
                :body {:id "sighting-12345"
                       :timestamp "2016-02-11T00:40:48.212-00:00"
                       :observed_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                       :description "a sighting"
                       :tlp "amber"
                       :source "source"
                       :sensor "endpoint.sensor"
                       :confidence "High"
                       :indicators [{:indicator_id "indicator-22334455"}]}
                :headers {"api_key" api-key})]
      (is (= 400 status)))))

(deftest-for-each-store test-sighting-route-posting-id-without-capability
  (helpers/set-capabilities! "foouser" "user" #{:create-sighting})
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "POST /ctia/sighting with valid ID, but not capability"
    (let [{status :status
           :as response}
          (post "ctia/sighting"
                :body {:id "sighting-7d24c22a-96e3-40fb-81d3-eae158f0770c"
                       :timestamp "2016-02-11T00:40:48.212-00:00"
                       :observed_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                       :description "a sighting"
                       :tlp "amber"
                       :source "source"
                       :sensor "endpoint.sensor"
                       :confidence "High"
                       :indicators [{:indicator_id "indicator-22334455"}]}
                :headers {"api_key" api-key})]
      (is (= 400 status)))))

(deftest-for-each-store test-sighting-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "POST /ctia/sighting"
    (let [{status :status
           sighting :parsed-body
           :as response}
          (post "ctia/sighting"
                :body {:id "sighting-7d24c22a-96e3-40fb-81d3-eae158f0770c"
                       :timestamp "2016-02-11T00:40:48.212-00:00"
                       :observed_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                       :description "a sighting"
                       :tlp "amber"
                       :source "source"
                       :sensor "endpoint.sensor"
                       :confidence "High"
                       :indicators [{:indicator_id "indicator-22334455"}]}
                :headers {"api_key" api-key})]
      (is (empty? (:errors sighting)) "No errors when")
      (is (= 201 status))

      (testing "GET /ctia/sighting/:id"
        (let [{status :status
               sighting :parsed-body}
              (get (str "ctia/sighting/" (:id sighting))
                   :headers {"api_key" api-key})]
          (is (empty? (:errors sighting)) "No errors when")
          (is (= 200 status))
          (is (deep= {:id "sighting-7d24c22a-96e3-40fb-81d3-eae158f0770c"
                      :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                      :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}
                      :description "a sighting"
                      :tlp "amber"
                      :count 1
                      :schema_version schema-version
                      :source "source"
                      :sensor "endpoint.sensor"
                      :confidence "High"
                      :indicators [{:indicator_id "indicator-22334455"}]}
                     (dissoc sighting :created :modified :owner :type)))))
      
      (testing "PUT /ctia/sighting/:id"
        (let [{status :status
               updated-sighting :parsed-body}
              (put (str "ctia/sighting/" (:id sighting))
                   :body {:id "sighting-7d24c22a-96e3-40fb-81d3-eae158f0770c"
                          :timestamp "2016-02-11T00:40:48.212-00:00"
                          :observed_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                          :description "updated sighting"
                          :tlp "green"
                          :source "source"
                          :sensor "endpoint.sensor"
                          :confidence "High"
                          :indicators [{:indicator_id "indicator-22334455"}]}
                   :headers {"api_key" api-key})]
          (is (empty? (:errors sighting)) "No errors when")
          (is (= 200 status))
          (is (deep=
               {:id "sighting-7d24c22a-96e3-40fb-81d3-eae158f0770c"
                :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}
                :description "updated sighting"
                :tlp "green"
                :schema_version schema-version
                :source "source"
                :sensor "endpoint.sensor"
                :confidence "High"
                :count 1
                :indicators [{:indicator_id "indicator-22334455"}]}
               (dissoc updated-sighting :created :modified :owner :type)))))

      (testing "DELETE /ctia/sighting/:id"
        (let [{status :status} (delete (str "ctia/sighting/" (:id sighting))
                                       :headers {"api_key" api-key})]
          (is (= 204 status))
          (let [{status :status} (get (str "ctia/sighting/" (:id sighting))
                                      :headers {"api_key" api-key})]
            (is (= 404 status))))))))

(deftest-for-each-store test-sighting-creation-without-any-observable-or-indicator-is-rejected
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "Creation of sighting without observable or indicator are rejected"
    (let [{status :status}
          (post "ctia/sighting"
                :body {:id "sighting-7d24c22a-96e3-40fb-81d3-eae158f0770c"
                       :timestamp "2016-02-11T00:40:48.212-00:00"
                       :observed_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                       :description "a sighting"
                       :tlp "amber"
                       :source "source"
                       :sensor "endpoint.sensor"
                       :confidence "High"}
                :headers {"api_key" api-key})]
      (= 422 status))))

(deftest-for-each-store test-sighting-update-without-any-observable-or-indicator-is-rejected
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "Update of sighting without obserable or indicator are rejected"
    (let [{post-status :status}
          (post "ctia/sighting"
                :body {:id "sighting-7d24c22a-96e3-40fb-81d3-eae158f0770c"
                       :timestamp "2016-02-11T00:40:48.212-00:00"
                       :observed_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                       :description "a sighting"
                       :tlp "amber"
                       :source "source"
                       :sensor "endpoint.sensor"
                       :confidence "High"
                       :indicators [{:indicator_id "indicator-22334455"}]}
                :headers {"api_key" api-key})
          {put-status :status}
          (put "ctia/sighting/sighting-12345"
               :body {:id "sighting-7d24c22a-96e3-40fb-81d3-eae158f0770c"
                      :timestamp "2016-02-11T00:40:48.212-00:00"
                      :observed_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                      :description "updated sighting"
                      :tlp "amber"
                      :source "source"
                      :sensor "endpoint.sensor"
                      :confidence "High"}
               :headers {"api_key" api-key})]
      (is (= 201 post-status))
      (is (= 422 put-status)))))
