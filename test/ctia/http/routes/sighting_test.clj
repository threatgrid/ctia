(ns ctia.http.routes.sighting-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [search :refer [test-query-string-search]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [http :refer [api-key]]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
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
                       :external_ids ["http://ex.tld/ctia/sighting/sighting-123"
                                      "http://ex.tld/ctia/sighting/sighting-345"]
                       :timestamp "2016-02-11T00:40:48.212-00:00"
                       :observed_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                       :description "description"
                       :tlp "green"
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
                       :external_ids ["http://ex.tld/ctia/sighting/sighting-123"
                                      "http://ex.tld/ctia/sighting/sighting-345"]
                       :timestamp "2016-02-11T00:40:48.212-00:00"
                       :observed_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                       :description "description"
                       :tlp "green"
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
           sighting :parsed-body}
          (post "ctia/sighting"
                :body {:id "sighting-7d24c22a-96e3-40fb-81d3-eae158f0770c"
                       :external_ids ["http://ex.tld/ctia/sighting/sighting-123"
                                      "http://ex.tld/ctia/sighting/sighting-345"]
                       :timestamp "2016-02-11T00:40:48.212-00:00"
                       :observed_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                       :description "description"
                       :tlp "green"
                       :source "source"
                       :sensor "endpoint.sensor"
                       :confidence "High"
                       :indicators [{:indicator_id "indicator-22334455"}]}
                :headers {"api_key" api-key})

          sighting-id (id/long-id->id (:id sighting))
          sighting-external-ids (:external_ids sighting)]
      (is (empty? (:errors sighting)) "No errors when")
      (is (= 201 status))
      (is (deep=
           {:id (id/long-id sighting-id)
            :external_ids ["http://ex.tld/ctia/sighting/sighting-123"
                           "http://ex.tld/ctia/sighting/sighting-345"]
            :timestamp #inst "2016-02-11T00:40:48.212-00:00"
            :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}
            :description "description"
            :tlp "green"
            :source "source"
            :sensor "endpoint.sensor"
            :confidence "High"
            :indicators [{:indicator_id "indicator-22334455"}]
            :owner "foouser"
            :type "sighting"
            :schema_version schema-version
            :count 1}
           (dissoc sighting
                   :created
                   :modified)))

      (testing "the sighting ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    sighting-id)      (:hostname    show-props)))
          (is (= (:protocol    sighting-id)      (:protocol    show-props)))
          (is (= (:port        sighting-id)      (:port        show-props)))
          (is (= (:path-prefix sighting-id) (seq (:path-prefix show-props))))))


      (testing "GET /ctia/sighting/external_id"
        (let [response (get "ctia/sighting/external_id"
                            :headers {"api_key" "45c1f5e3f05d0"}
                            :query-params {"external_id" (rand-nth sighting-external-ids)})
              sightings (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id sighting-id)
                 :external_ids ["http://ex.tld/ctia/sighting/sighting-123"
                                "http://ex.tld/ctia/sighting/sighting-345"]
                 :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                 :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}
                 :description "description"
                 :tlp "green"
                 :count 1
                 :schema_version schema-version
                 :source "source"
                 :sensor "endpoint.sensor"
                 :type "sighting"
                 :owner "foouser"
                 :confidence "High"
                 :indicators [{:indicator_id "indicator-22334455"}]}]
               (map #(dissoc % :created :modified) sightings)))))

      (test-query-string-search :sighting "description" :description)
      
      (testing "GET /ctia/sighting/:id"
        (let [{status :status
               sighting :parsed-body}
              (get (str "ctia/sighting/" (:short-id sighting-id))
                   :headers {"api_key" api-key})]
          (is (empty? (:errors sighting)) "No errors when")
          (is (= 200 status))
          (is (deep= {:id (id/long-id sighting-id)
                      :external_ids ["http://ex.tld/ctia/sighting/sighting-123"
                                     "http://ex.tld/ctia/sighting/sighting-345"]
                      :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                      :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}
                      :description "description"
                      :tlp "green"
                      :count 1
                      :schema_version schema-version
                      :source "source"
                      :sensor "endpoint.sensor"
                      :confidence "High"
                      :indicators [{:indicator_id "indicator-22334455"}]
                      :owner "foouser"
                      :type "sighting"}
                     (dissoc sighting
                             :created
                             :modified)))))

      (testing "PUT /ctia/sighting/:id"
        (let [{status :status
               updated-sighting :parsed-body}
              (put (str "ctia/sighting/" (:short-id sighting-id))
                   :body {:id "sighting-7d24c22a-96e3-40fb-81d3-eae158f0770c"
                          :external_ids ["http://ex.tld/ctia/sighting/sighting-123"
                                         "http://ex.tld/ctia/sighting/sighting-345"]
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
               {:id (id/long-id sighting-id)
                :external_ids ["http://ex.tld/ctia/sighting/sighting-123"
                               "http://ex.tld/ctia/sighting/sighting-345"]
                :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}
                :description "updated sighting"
                :tlp "green"
                :schema_version schema-version
                :source "source"
                :sensor "endpoint.sensor"
                :confidence "High"
                :count 1
                :indicators [{:indicator_id "indicator-22334455"}]
                :owner "foouser"
                :type "sighting"}
               (dissoc updated-sighting
                       :created
                       :modified)))))

      (testing "DELETE /ctia/sighting/:id"
        (let [{status :status} (delete (str "ctia/sighting/" (:short-id sighting-id))
                                       :headers {"api_key" api-key})]
          (is (= 204 status))
          (let [{status :status} (get (str "ctia/sighting/" (:short-id sighting-id))
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
                       :description "description"
                       :tlp "green"
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
                       :description "description"
                       :tlp "green"
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
                      :tlp "green"
                      :source "source"
                      :sensor "endpoint.sensor"
                      :confidence "High"}
               :headers {"api_key" api-key})]
      (is (= 201 post-status))
      (is (= 422 put-status)))))
