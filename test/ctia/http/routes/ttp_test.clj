(ns ctia.http.routes.ttp-test
  (:refer-clojure :exclude [get])
  (:require
   [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
   [schema-generators.generators :as g]
   [ctia.test-helpers.core :refer [delete get post put] :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [deftest-for-each-store]]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.schemas.ttp :refer [NewTTP StoredTTP]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-ttp-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/ttp"
    (let [response (post "ctia/ttp"
                         :body {:title "ttp"
                                :description "description"
                                :ttp_type "foo"
                                :indicators [{:indicator_id "indicator-1"}
                                             {:indicator_id "indicator-2"}]
                                :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                                  {:exploit_target_id "exploit-target-234"}]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                             :end_time "2016-07-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          ttp (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           {:type "ttp"
            :title "ttp"
            :tlp "green"
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

      (testing "GET /ctia/ttp/:id"
        (let [response (get (str "ctia/ttp/" (:id ttp))
                            :headers {"api_key" "45c1f5e3f05d0"})
              ttp (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:type "ttp"
                :title "ttp"
                :tlp "green"
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
                   :body {:title "updated ttp"
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
                :type "ttp"
                :created (:created ttp)
                :title "updated ttp"
                :tlp "green"
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

(deftest-for-each-store test-ttp-routes-generative
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (let [new-ttps (g/sample 20 NewTTP)]
    (testing "POST /ctia/ttp GET /ctia/ttp"
      (let [responses (map #(post "ctia/ttp"
                                  :body %
                                  :headers {"api_key" "45c1f5e3f05d0"}) new-ttps)]
        (doall (map #(is (= 200 (:status %))) responses))
        (is (deep=
             (set new-ttps)
             (->> responses
                  (map :parsed-body)
                  (map #(get (str "ctia/ttp/" (:id %))
                             :headers {"api_key" "45c1f5e3f05d0"}))
                  (map :parsed-body)
                  (map #(dissoc % :id :created :modified :owner))
                  set)))))))
