(ns ctia.http.routes.indicator-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers
             [core :as mth]
             [http :refer [encode]]]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia
             [auth :as auth]
             [properties :refer [get-http-show]]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [search :refer [test-query-string-search]]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]
            [ring.util.codec :refer [url-encode]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-indicator-routes
  (helpers/set-capabilities! "foouser" "user" auth/all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/indicator"
    (let [{status :status
           indicator :parsed-body}
          (post "ctia/indicator"
                :body {:external_ids ["http://ex.tld/ctia/indicator/indicator-123"
                                      "http://ex.tld/ctia/indicator/indicator-345"]
                       :title "indicator-title"
                       :description "description"
                       :producer "producer"
                       :indicator_type ["C2" "IP Watchlist"]
                       :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                    :end_time "2016-07-11T00:40:48.212-00:00"}
                       :composite_indicator_expression {:operator "and"
                                                        :indicator_ids ["test1" "test2"]}}
                :headers {"Authorization" "45c1f5e3f05d0"})

          indicator-id (id/long-id->id (:id indicator))
          indicator-external-ids (:external_ids indicator)]
      (is (= 201 status))
      (is (deep=
           {:id (id/long-id indicator-id)
            :type "indicator"
            :external_ids ["http://ex.tld/ctia/indicator/indicator-123"
                           "http://ex.tld/ctia/indicator/indicator-345"]
            :title "indicator-title"
            :description "description"
            :producer "producer"
            :tlp "green"
            :schema_version schema-version
            :indicator_type ["C2" "IP Watchlist"]
            :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                         :end_time #inst "2016-07-11T00:40:48.212-00:00"}
            :composite_indicator_expression {:operator "and"
                                             :indicator_ids ["test1" "test2"]}}
           indicator))

      (testing "the indicator ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    indicator-id)      (:hostname    show-props)))
          (is (= (:protocol    indicator-id)      (:protocol    show-props)))
          (is (= (:port        indicator-id)      (:port        show-props)))
          (is (= (:path-prefix indicator-id) (seq (:path-prefix show-props))))))

      (test-query-string-search :indicator "description" :description)

      (testing "GET /ctia/indicator/external_id/:external_id"
        (let [response (get (format "ctia/indicator/external_id/%s"
                                    (encode (rand-nth indicator-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              indicators (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id indicator-id)
                 :type "indicator"
                 :external_ids ["http://ex.tld/ctia/indicator/indicator-123"
                                "http://ex.tld/ctia/indicator/indicator-345"]
                 :title "indicator-title"
                 :description "description"
                 :producer "producer"
                 :tlp "green"
                 :schema_version schema-version
                 :indicator_type ["C2" "IP Watchlist"]
                 :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                              :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                 :composite_indicator_expression {:operator "and"
                                                  :indicator_ids ["test1" "test2"]}}]
               indicators))))

      (testing "GET /ctia/indicator/:id"
        (let [response (get (str "ctia/indicator/" (:short-id indicator-id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              indicator (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id indicator-id)
                :type "indicator"
                :external_ids ["http://ex.tld/ctia/indicator/indicator-123"
                               "http://ex.tld/ctia/indicator/indicator-345"]
                :title "indicator-title"
                :description "description"
                :producer "producer"
                :tlp "green"
                :schema_version schema-version
                :indicator_type ["C2" "IP Watchlist"]
                :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :composite_indicator_expression {:operator "and"
                                                 :indicator_ids ["test1" "test2"]}}
               indicator))))

      (testing "PUT /ctia/indicator/:id"
        (let [{status :status
               updated-indicator :parsed-body}
              (put (str "ctia/indicator/" (:short-id indicator-id))
                   :body {:external_ids ["http://ex.tld/ctia/indicator/indicator-123"
                                         "http://ex.tld/ctia/indicator/indicator-345"]
                          :title "updated indicator"
                          :description "updated description"
                          :producer "producer"
                          :tlp "amber"
                          :indicator_type ["IP Watchlist"]
                          :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}
                          :composite_indicator_expression {:operator "and"
                                                           :indicator_ids ["test1" "test2"]}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:id (id/long-id indicator-id)
                :external_ids ["http://ex.tld/ctia/indicator/indicator-123"
                               "http://ex.tld/ctia/indicator/indicator-345"]
                :type "indicator"
                :title "updated indicator"
                :description "updated description"
                :producer "producer"
                :tlp "amber"
                :schema_version schema-version
                :indicator_type ["IP Watchlist"]
                :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :composite_indicator_expression {:operator "and"
                                                 :indicator_ids ["test1" "test2"]}}
               updated-indicator))))

      (testing "DELETE /ctia/indicator/:id"
        (let [response (delete (str "ctia/indicator/" (:short-id indicator-id))
                               :headers {"Authorization" "45c1f5e3f05d0"})]
          ;; Deleting indicators is not allowed
          (is (= 404 (:status response))))))))
