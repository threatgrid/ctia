(ns ctia.http.routes.sighting-test
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
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

(deftest-for-each-store test-sighting-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "POST /ctia/sighting"
    (let [{status :status
           sighting :parsed-body
           :as response}
          (post "ctia/sighting"
                :body {:id "sighting-12345"
                       :timestamp "2016-02-11T00:40:48.212-00:00"
                       :description "a sighting"
                       :tlp "yellow"
                       :source "source"
                       :source_device "endpoint.sensor"
                       :confidence "High"
                       :indicators [{:indicator_id "indicator-22334455"}]}
                :headers {"api_key" api-key})]
      (is (= 200 status))

      (testing "GET /ctia/sighting/:id"
        (let [{status :status
               sighting :parsed-body}
              (get (str "ctia/sighting/" (:id sighting))
                   :headers {"api_key" api-key})]
          (is (= 200 status))
          (is (= {:id "sighting-12345"
                  :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                  :description "a sighting"
                  :tlp "yellow"
                  :source "source"
                  :source_device "endpoint.sensor"
                  :confidence "High"
                  :indicators [{:indicator_id "indicator-22334455"}]}
                 (dissoc sighting :created :modified :owner :type)))))

      (testing "PUT /ctia/sighting/:id"
        (let [{status :status
               updated-sighting :parsed-body}
              (put (str "ctia/sighting/" (:id sighting))
                   :body {:id "sighting-12345"
                          :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                          :description "updated sighting"
                          :tlp "green"
                          :source "source"
                          :source_device "endpoint.sensor"
                          :confidence "High"
                          :indicators [{:indicator_id "indicator-22334455"}]}
                   :headers {"api_key" api-key})]
          (is (= 200 status))
          (is (deep=
               {:id "sighting-12345"
                :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                :description "updated sighting"
                :tlp "green"
                :source "source"
                :source_device "endpoint.sensor"
                :confidence "High"
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
                :body {:id "sighting-12345"
                       :timestamp "2016-02-11T00:40:48.212-00:00"
                       :description "a sighting"
                       :tlp "yellow"
                       :source "source"
                       :source_device "endpoint.sensor"
                       :confidence "High"}
                :headers {"api_key" api-key})]
      (= 422 status))))

(deftest-for-each-store test-sighting-update-without-any-observable-or-indicator-is-rejected
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "Update of sighting without obserable or indicator are rejected"
    (let [{post-status :status}
          (post "ctia/sighting"
                :body {:id "sighting-12345"
                       :timestamp "2016-02-11T00:40:48.212-00:00"
                       :description "a sighting"
                       :tlp "yellow"
                       :source "source"
                       :source_device "endpoint.sensor"
                       :confidence "High"
                       :indicators [{:indicator_id "indicator-22334455"}]}
                :headers {"api_key" api-key})
          {put-status :status}
          (put "ctia/sighting/sighting-12345"
               :body {:id "sighting-12345"
                      :timestamp "2016-02-11T00:40:48.212-00:00"
                      :description "updated sighting"
                      :tlp "yellow"
                      :source "source"
                      :source_device "endpoint.sensor"
                      :confidence "High"}
               :headers {"api_key" api-key})]
      (is (= 200 post-status))
      (is (= 422 put-status)))))
