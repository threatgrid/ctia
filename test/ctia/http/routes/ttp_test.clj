(ns ctia.http.routes.ttp-test
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

(deftest-for-each-store test-ttp-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/ttp"
    (let [response (post "ctia/ttp"
                         :body {:external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                                               "http://ex.tld/ctia/ttp/ttp-345"]
                                :title "ttp"
                                :description "description"
                                :ttp_type "foo"
                                :indicators [{:indicator_id "indicator-1"}
                                             {:indicator_id "indicator-2"}]
                                :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                                  {:exploit_target_id "exploit-target-234"}]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                             :end_time "2016-07-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          ttp (:parsed-body response)
          ttp-external-ids (:external_ids ttp)]
      (is (= 201 (:status response)))
      (is (deep=
           {:external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                           "http://ex.tld/ctia/ttp/ttp-345"]
            :type "ttp"
            :title "ttp"
            :tlp "green"
            :schema_version schema-version
            :description "description"
            :ttp_type "foo"
            :indicators [{:indicator_id "indicator-1"}
                         {:indicator_id "indicator-2"}]
            :exploit_targets [{:exploit_target_id "exploit-target-123"}
                              {:exploit_target_id "exploit-target-234"}]
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2016-07-11T00:40:48.212-00:00"}
            :owner "foouser"}
           (dissoc ttp
                   :id
                   :created
                   :modified)))

      (testing "GET /ctia/ttp/external_id"
        (let [response (get "ctia/ttp/external_id"
                            :headers {"api_key" "45c1f5e3f05d0"}
                            :query-params {"external_id" (rand-nth ttp-external-ids)})
              ttps (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                                "http://ex.tld/ctia/ttp/ttp-345"]
                 :type "ttp"
                 :title "ttp"
                 :tlp "green"
                 :schema_version schema-version
                 :description "description"
                 :ttp_type "foo"
                 :indicators [{:indicator_id "indicator-1"}
                              {:indicator_id "indicator-2"}]
                 :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                   {:exploit_target_id "exploit-target-234"}]
                 :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                              :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                 :owner "foouser"}]
               (map #(dissoc % :id :created :modified) ttps)))))

      (testing "GET /ctia/ttp/:id"
        (let [response (get (str "ctia/ttp/" (:id ttp))
                            :headers {"api_key" "45c1f5e3f05d0"})
              ttp (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                               "http://ex.tld/ctia/ttp/ttp-345"]
                :type "ttp"
                :title "ttp"
                :tlp "green"
                :schema_version schema-version
                :description "description"
                :ttp_type "foo"
                :indicators [{:indicator_id "indicator-1"}
                             {:indicator_id "indicator-2"}]
                :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                  {:exploit_target_id "exploit-target-234"}]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :owner "foouser"}
               (dissoc ttp
                       :id
                       :created
                       :modified)))))

      (testing "PUT /ctia/ttp/:id"
        (let [{status :status
               updated-ttp :parsed-body}
              (put (str "ctia/ttp/" (:id ttp))
                   :body {:external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                                         "http://ex.tld/ctia/ttp/ttp-345"]
                          :title "updated ttp"
                          :description "updated description"
                          :ttp_type "bar"
                          :indicators [{:indicator_id "indicator-1"}
                                       {:indicator_id "indicator-2"}]

                          :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                            {:exploit_target_id "exploit-target-234"}]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:id (:id ttp)
                :external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                               "http://ex.tld/ctia/ttp/ttp-345"]
                :type "ttp"
                :created (:created ttp)
                :title "updated ttp"
                :tlp "green"
                :schema_version schema-version
                :description "updated description"
                :ttp_type "bar"
                :indicators [{:indicator_id "indicator-1"}
                             {:indicator_id "indicator-2"}]

                :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                  {:exploit_target_id "exploit-target-234"}]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :owner "foouser"}
               (dissoc updated-ttp
                       :modified)))))

      (testing "DELETE /ctia/ttp/:id"
        (let [response (delete (str "ctia/ttp/" (:id ttp))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/ttp/" (:id ttp))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
