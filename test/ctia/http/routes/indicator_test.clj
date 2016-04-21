(ns ctia.http.routes.indicator-test
  (:refer-clojure :exclude [get])
  (:require
   [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
   [schema-generators.generators :as g]
   [ctia.test-helpers.core :refer [delete get post put] :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [deftest-for-each-store]]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.schemas.indicator :refer [NewIndicator StoredIndicator]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-indicator-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/indicator"
    (let [response (post "ctia/indicator"
                         :body {:title "indicator-title"
                                :description "description"
                                :producer "producer"
                                :indicator_type ["C2" "IP Watchlist"]
                                :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                             :end_time "2016-07-11T00:40:48.212-00:00"}
                                :related_campaigns [{:confidence "High"
                                                     :source "source"
                                                     :relationship "relationship"
                                                     :campaign_id "campaign-123"}]
                                :composite_indicator_expression {:operator "and"
                                                                 :indicator_ids ["test1" "test2"]}
                                :related_COAs [{:confidence "High"
                                                :source "source"
                                                :relationship "relationship"
                                                :COA_id "coa-123"}]}
                         :headers {"api_key" "45c1f5e3f05d0"})
          indicator (:parsed-body response)]

      (is (= 200 (:status response)))
      (is (deep=
           {:type "indicator"
            :title "indicator-title"
            :description "description"
            :producer "producer"
            :indicator_type ["C2" "IP Watchlist"]
            :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                         :end_time #inst "2016-07-11T00:40:48.212-00:00"}
            :related_campaigns [{:confidence "High"
                                 :source "source"
                                 :relationship "relationship"
                                 :campaign_id "campaign-123"}]
            :composite_indicator_expression {:operator "and"
                                             :indicator_ids ["test1" "test2"]}
            :related_COAs [{:confidence "High"
                            :source "source"
                            :relationship "relationship"
                            :COA_id "coa-123"}]
            :owner "foouser"}
           (dissoc indicator
                   :id
                   :created
                   :modified)))

      (testing "GET /ctia/indicator/:id"
        (let [response (get (str "ctia/indicator/" (:id indicator))
                            :headers {"api_key" "45c1f5e3f05d0"})
              indicator (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:type "indicator"
                :title "indicator-title"
                :description "description"
                :producer "producer"
                :indicator_type ["C2" "IP Watchlist"]
                :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :related_campaigns [{:confidence "High"
                                     :source "source"
                                     :relationship "relationship"
                                     :campaign_id "campaign-123"}]
                :composite_indicator_expression {:operator "and"
                                                 :indicator_ids ["test1" "test2"]}
                :related_COAs [{:confidence "High"
                                :source "source"
                                :relationship "relationship"
                                :COA_id "coa-123"}]
                :owner "foouser"}
               (dissoc indicator
                       :id
                       :created
                       :modified)))))

      (testing "GET /ctia/indicator/title/:title"
        (let [{status :status
               indicators :parsed-body
               :as response}
              (get "ctia/indicator/title/indicator-title"
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               [{:type "indicator"
                 :title "indicator-title"
                 :description "description"
                 :producer "producer"
                 :indicator_type ["C2" "IP Watchlist"]
                 :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                              :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                 :related_campaigns [{:confidence "High"
                                      :source "source"
                                      :relationship "relationship"
                                      :campaign_id "campaign-123"}]
                 :composite_indicator_expression {:operator "and"
                                                  :indicator_ids ["test1" "test2"]}
                 :related_COAs [{:confidence "High"
                                 :source "source"
                                 :relationship "relationship"
                                 :COA_id "coa-123"}]
                 :owner "foouser"}]
               (map #(dissoc % :id :created :modified) indicators)))))

      (testing "PUT /ctia/indicator/:id"
        (let [{status :status
               updated-indicator :parsed-body}
              (put (str "ctia/indicator/" (:id indicator))
                   :body {:title "updated indicator"
                          :description "updated description"
                          :producer "producer"
                          :indicator_type ["IP Watchlist"]
                          :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}
                          :related_campaigns [{:confidence "Low"
                                               :source "source"
                                               :relationship "relationship"
                                               :campaign_id "campaign-123"}]
                          :composite_indicator_expression {:operator "and"
                                                           :indicator_ids ["test1" "test2"]}
                          :related_COAs [{:confidence "High"
                                          :source "source"
                                          :relationship "relationship"
                                          :COA_id "coa-123"}]}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:id (:id indicator)
                :type "indicator"
                :created (:created indicator)
                :title "updated indicator"
                :description "updated description"
                :producer "producer"
                :indicator_type ["IP Watchlist"]
                :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :related_campaigns [{:confidence "Low"
                                     :source "source"
                                     :relationship "relationship"
                                     :campaign_id "campaign-123"}]
                :composite_indicator_expression {:operator "and"
                                                 :indicator_ids ["test1" "test2"]}
                :related_COAs [{:confidence "High"
                                :source "source"
                                :relationship "relationship"
                                :COA_id "coa-123"}]
                :owner "foouser"}
               (dissoc updated-indicator
                       :modified)))))

      (testing "DELETE /ctia/indicator/:id"
        (let [response (delete (str "ctia/indicator/" (:id indicator))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          ;; Deleting indicators is not allowed
          (is (= 404 (:status response))))))))

(deftest-for-each-store test-indicator-routes-generative
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (let [new-indicators (g/sample 20 NewIndicator)]
    (testing "POST /ctia/indicator GET /ctia/indicator"

      (let [responses (map #(post "ctia/indicator"
                                  :body %
                                  :headers {"api_key" "45c1f5e3f05d0"}) new-indicators)]


        (doall (map #(is (= 200 (:status %))) responses))
        (is (deep=
             (set new-indicators)
             (->> responses
                  (map :parsed-body)
                  (map #(get (str "ctia/indicator/" (:id %))
                             :headers {"api_key" "45c1f5e3f05d0"}))
                  (map :parsed-body)
                  (map #(dissoc % :id :created :modified :owner))
                  set)))))))
