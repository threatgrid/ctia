(ns ctia.http.routes.indicator-test
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.http.routes.indicator :refer [->long-id]]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [http :refer [api-key assert-post test-get-list]]
             [store :refer [deftest-for-each-store]]]
            [ring.util.codec :refer [url-encode]]))

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
      (is (= 201 (:status response)))
      (is (deep=
           {:type "indicator"
            :title "indicator-title"
            :description "description"
            :producer "producer"
            :tlp "green"
            :schema_version schema-version
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
                :tlp "green"
                :schema_version schema-version
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
                 :tlp "green"
                 :schema_version schema-version
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
                          :tlp "amber"
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
                :tlp "amber"
                :schema_version schema-version
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

(deftest-for-each-store test-indicator-list-by-coa-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "POST /ctia/indicator"
    (let [new-coa-1 {:title "coa-1"
                     :description "coa-1"
                     :valid_time {:start_time "2016-05-19T00:40:48.212-00:00"
                                  :end_time "2017-05-19T00:40:48.212-00:00"}}
          new-coa-2 {:title "coa-2"
                     :description "coa-2"
                     :valid_time {:start_time "2016-05-19T10:40:48.212-00:00"
                                  :end_time "2017-05-19T10:40:48.212-00:00"}}
          coa-1 (assert-post "ctia/coa" new-coa-1)
          coa-2 (assert-post "ctia/coa" new-coa-2)

          new-indicator-1 {:title "indicator-1"
                           :description "indicator-1"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_COAs [{:COA_id (->long-id :coa (:id coa-1))}
                                          {:COA_id (->long-id :coa (:id coa-2))}]}
          new-indicator-2 {:title "indicator-2"
                           :description "indicator-2"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_COAs [{:COA_id (->long-id :coa (:id coa-1))}]}
          new-indicator-3 {:title "indicator-3"
                           :description "indicator-3"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_COAs [{:COA_id (->long-id :coa (:id coa-2))}]}
          new-indicator-4 {:title "indicator-4"
                           :description "indicator-4"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_COAs []}

          indicator-1 (assert-post "ctia/indicator" new-indicator-1)
          indicator-2 (assert-post "ctia/indicator" new-indicator-2)
          indicator-3 (assert-post "ctia/indicator" new-indicator-3)
          indicator-4 (assert-post "ctia/indicator" new-indicator-4)]
      (test-get-list (str "ctia/coa/" (url-encode (:id coa-1)) "/indicators")
                     [indicator-1 indicator-2])
      (test-get-list (str "ctia/coa/" (url-encode (:id coa-2)) "/indicators")
                     [indicator-1 indicator-3]))))

(deftest-for-each-store test-indicator-list-by-campaign-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "POST /ctia/indicator"
    (let [new-campaign-1 {:title "campaign-1"
                          :description "campaign-1"
                          :campaign_type "campaign-type"
                          :indicators []
                          :valid_time {:start_time "2016-05-19T00:40:48.212-00:00"
                                       :end_time "2017-05-19T00:40:48.212-00:00"}}
          new-campaign-2 {:title "campaign-2"
                          :description "campaign-2"
                          :campaign_type "campaign-type"
                          :indicators []
                          :valid_time {:start_time "2016-05-19T10:40:48.212-00:00"
                                       :end_time "2017-05-19T10:40:48.212-00:00"}}
          campaign-1 (assert-post "ctia/campaign" new-campaign-1)
          campaign-2 (assert-post "ctia/campaign" new-campaign-2)

          new-indicator-1 {:title "indicator-1"
                           :description "indicator-1"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_campaigns [{:campaign_id (->long-id :campaign (:id campaign-1))}
                                               {:campaign_id (->long-id :campaign (:id campaign-2))}]}
          new-indicator-2 {:title "indicator-2"
                           :description "indicator-2"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_campaigns [{:campaign_id (->long-id :campaign (:id campaign-1))}]}
          new-indicator-3 {:title "indicator-3"
                           :description "indicator-3"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_campaigns [{:campaign_id (->long-id :campaign (:id campaign-2))}]}
          new-indicator-4 {:title "indicator-4"
                           :description "indicator-4"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_campaigns []}

          indicator-1 (assert-post "ctia/indicator" new-indicator-1)
          indicator-2 (assert-post "ctia/indicator" new-indicator-2)
          indicator-3 (assert-post "ctia/indicator" new-indicator-3)
          indicator-4 (assert-post "ctia/indicator" new-indicator-4)]
      (test-get-list (str "ctia/campaign/" (url-encode (:id campaign-1)) "/indicators")
                     [indicator-1 indicator-2])
      (test-get-list (str "ctia/campaign/" (url-encode (:id campaign-2)) "/indicators")
                     [indicator-1 indicator-3]))))

(deftest-for-each-store test-indicator-list-by-judgement-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "POST /ctia/indicator"
    (let [new-judgement-1 {:observable {:value "1.2.3.4" :type "ip"}
                           :disposition 2
                           :source "test"
                           :priority 100
                           :severity 100
                           :confidence "Low"
                           :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
          new-judgement-2 {:observable {:value "4.3.2.1" :type "ip"}
                           :disposition 2
                           :source "test"
                           :priority 100
                           :severity 100
                           :confidence "Low"
                           :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
          judgement-1 (assert-post "ctia/judgement" new-judgement-1)
          judgement-2 (assert-post "ctia/judgement" new-judgement-2)

          new-indicator-1 {:title "indicator-1"
                           :description "indicator-1"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :judgements [{:judgement_id (->long-id :judgement (:id judgement-1))}
                                        {:judgement_id (->long-id :judgement (:id judgement-2))}]}
          new-indicator-2 {:title "indicator-2"
                           :description "indicator-2"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :judgements [{:judgement_id (->long-id :judgement (:id judgement-1))}]}
          new-indicator-3 {:title "indicator-3"
                           :description "indicator-3"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :judgements [{:judgement_id (->long-id :judgement (:id judgement-2))}]}
          new-indicator-4 {:title "indicator-4"
                           :description "indicator-4"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :judgements []}

          indicator-1 (assert-post "ctia/indicator" new-indicator-1)
          indicator-2 (assert-post "ctia/indicator" new-indicator-2)
          indicator-3 (assert-post "ctia/indicator" new-indicator-3)
          indicator-4 (assert-post "ctia/indicator" new-indicator-4)]
      (test-get-list (str "ctia/judgement/" (url-encode (:id judgement-1)) "/indicators")
                     [indicator-1 indicator-2])
      (test-get-list (str "ctia/judgement/" (url-encode (:id judgement-2)) "/indicators")
                     [indicator-1 indicator-3]))))

(deftest-for-each-store test-indicator-list-by-ttp-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "POST /ctia/indicator"
    (let [new-ttp-1 {:title "ttp-1"
                     :description "ttp-1"
                     :ttp_type "ttp-type"
                     :indicators []
                     :valid_time {:start_time "2016-05-19T00:40:48.212-00:00"
                                  :end_time "2017-05-19T00:40:48.212-00:00"}}
          new-ttp-2 {:title "ttp-2"
                     :description "ttp-2"
                     :ttp_type "ttp-type"
                     :indicators []
                     :valid_time {:start_time "2016-05-19T10:40:48.212-00:00"
                                  :end_time "2017-05-19T10:40:48.212-00:00"}}
          ttp-1 (assert-post "ctia/ttp" new-ttp-1)
          ttp-2 (assert-post "ctia/ttp" new-ttp-2)

          new-indicator-1 {:title "indicator-1"
                           :description "indicator-1"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :indicated_TTP [{:ttp_id (->long-id :ttp (:id ttp-1))}
                                           {:ttp_id (->long-id :ttp (:id ttp-2))}]}
          new-indicator-2 {:title "indicator-2"
                           :description "indicator-2"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :indicated_TTP [{:ttp_id (->long-id :ttp (:id ttp-1))}]}
          new-indicator-3 {:title "indicator-3"
                           :description "indicator-3"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :indicated_TTP [{:ttp_id (->long-id :ttp (:id ttp-2))}]}
          new-indicator-4 {:title "indicator-4"
                           :description "indicator-4"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :indicated_TTP []}

          indicator-1 (assert-post "ctia/indicator" new-indicator-1)
          indicator-2 (assert-post "ctia/indicator" new-indicator-2)
          indicator-3 (assert-post "ctia/indicator" new-indicator-3)
          indicator-4 (assert-post "ctia/indicator" new-indicator-4)]
      (test-get-list (str "ctia/ttp/" (url-encode (:id ttp-1)) "/indicators")
                     [indicator-1 indicator-2])
      (test-get-list (str "ctia/ttp/" (url-encode (:id ttp-2)) "/indicators")
                     [indicator-1 indicator-3]))))

(deftest-for-each-store test-indicator-list-by-indicator-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "POST /ctia/indicator"
    (let [new-r-indicator-1 {:title "related-indicator-1"
                             :description "related-indicator-1"
                             :indicator_type ["C2" "IP Watchlist"]
                             :producer "test"
                             :valid_time {:start_time "2016-05-19T00:40:48.212-00:00"
                                          :end_time "2017-05-19T00:40:48.212-00:00"}}
          new-r-indicator-2 {:title "related-indicator-2"
                             :description "related-indicator-2"
                             :indicator_type ["C2" "IP Watchlist"]
                             :producer "test"
                             :valid_time {:start_time "2016-05-19T10:40:48.212-00:00"
                                          :end_time "2017-05-19T10:40:48.212-00:00"}}
          r-indicator-1 (assert-post "ctia/indicator" new-r-indicator-1)
          r-indicator-2 (assert-post "ctia/indicator" new-r-indicator-2)

          new-indicator-1 {:title "indicator-1"
                           :description "indicator-1"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_indicators [{:indicator_id (->long-id :indicator (:id r-indicator-1))}
                                                {:indicator_id (->long-id :indicator (:id r-indicator-2))}]}
          new-indicator-2 {:title "indicator-2"
                           :description "indicator-2"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_indicators [{:indicator_id (->long-id :indicator (:id r-indicator-1))}]}
          new-indicator-3 {:title "indicator-3"
                           :description "indicator-3"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_indicators [{:indicator_id (->long-id :indicator (:id r-indicator-2))}]}
          new-indicator-4 {:title "indicator-4"
                           :description "indicator-4"
                           :producer "producer"
                           :indicator_type ["C2" "IP Watchlist"]
                           :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                        :end_time "2016-07-11T00:40:48.212-00:00"}
                           :related_indicators []}

          indicator-1 (assert-post "ctia/indicator" new-indicator-1)
          indicator-2 (assert-post "ctia/indicator" new-indicator-2)
          indicator-3 (assert-post "ctia/indicator" new-indicator-3)
          indicator-4 (assert-post "ctia/indicator" new-indicator-4)]
      (test-get-list (str "ctia/indicator/" (url-encode (:id r-indicator-1)) "/indicators")
                     [indicator-1 indicator-2])
      (test-get-list (str "ctia/indicator/" (url-encode (:id r-indicator-2)) "/indicators")
                     [indicator-1 indicator-3]))))
