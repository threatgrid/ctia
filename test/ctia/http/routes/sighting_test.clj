(ns ctia.http.routes.sighting-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers
             [core :as mth]
             [http :refer [encode]]]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [access-control :refer [access-control-test]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [http :refer [api-key]]
             [search :refer [test-query-string-search]]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]
            [ctim.examples.sightings
             :refer [new-sighting-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-sighting-routes
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key
                                      "foouser"
                                      "foogroup"
                                      "user")
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
                       :confidence "High"}
                :headers {"Authorization" api-key})

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
            :type "sighting"
            :schema_version schema-version
            :count 1}
           sighting))

      (testing "the sighting ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    sighting-id)      (:hostname    show-props)))
          (is (= (:protocol    sighting-id)      (:protocol    show-props)))
          (is (= (:port        sighting-id)      (:port        show-props)))
          (is (= (:path-prefix sighting-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/sighting/external_id/:external_id"
        (let [response (get (format "ctia/sighting/external_id/%s"
                                    (encode (rand-nth sighting-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
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
                 :confidence "High"}]
               sightings))))

      (test-query-string-search :sighting "description" :description)

      (testing "GET /ctia/sighting/:id"
        (let [{status :status
               sighting :parsed-body}
              (get (str "ctia/sighting/" (:short-id sighting-id))
                   :headers {"Authorization" api-key})]
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
                      :type "sighting"}
                     sighting))))

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
                          :confidence "High"}
                   :headers {"Authorization" api-key})]
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
                :type "sighting"}
               updated-sighting))))

      (testing "DELETE /ctia/sighting/:id"
        (let [{status :status} (delete (str "ctia/sighting/" (:short-id sighting-id))
                                       :headers {"Authorization" api-key})]
          (is (= 204 status))
          (let [{status :status} (get (str "ctia/sighting/" (:short-id sighting-id))
                                      :headers {"Authorization" api-key})]
            (is (= 404 status))))))))

(deftest-for-each-store test-sighting-routes-access-control
  (access-control-test "sighting"
                       new-sighting-minimal
                       true
                       true))
