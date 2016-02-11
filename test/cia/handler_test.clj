(ns cia.handler-test
  (:refer-clojure :exclude [get])
  (:require [cia.handler :as handler]
            [cia.test-helpers :refer [get post delete] :as helpers]
            [clj-http.client :as http]
            [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]))

(use-fixtures :each (join-fixtures [(helpers/fixture-server handler/app)
                                    helpers/fixture-in-memory-store
                                    helpers/fixture-schema-validation]))

(deftest test-version-routes
  (testing "we can request different content types"
    (let [response (get "cia/version" :accept :json)]
      (is (= "/cia" (get-in response [:parsed-body "base"]))))

    (let [response (get "cia/version" :accept :edn)]
      (is (= "/cia" (get-in response [:parsed-body :base]) ))))

  (testing "GET /cia/version"
    (let [response (get "cia/version")]
      (is (= 200 (:status response)))
      (is (= "0.1" (get-in response [:parsed-body :version]))))))

(deftest test-actor-routes
  (testing "POST /cia/actor"
    (let [response (post "cia/actor"
                         :body {:title "actor"
                                :description ["description"]
                                :type "Hacker"
                                :source {:description "a source"}
                                :confidence "High"
                                :timestamp "2016-02-11T00:40:48.212-00:00"
                                :expires "2016-07-11T00:40:48.212-00:00"})
          actor (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:description ["description"],
              :type "Hacker",
              :title "actor",
              :confidence "High",
              :source {:description "a source"}
              :timestamp #inst "2016-02-11T00:40:48.212-00:00"
              :expires #inst "2016-07-11T00:40:48.212-00:00"}
             (dissoc actor
                     :id)))

      (testing "GET /cia/actor/:id"
        (let [response (get (str "cia/actor/" (:id actor)))
              actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:description ["description"],
                  :type "Hacker",
                  :title "actor",
                  :confidence "High",
                  :source {:description "a source"}
                  :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                  :expires #inst "2016-07-11T00:40:48.212-00:00"}
                 (dissoc actor
                         :id)))))

      (testing "DELETE /cia/actor/:id"
        (let [response (delete (str "cia/actor/" (:id actor)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/actor/" (:id actor)))]
            (is (= 404 (:status response)))))))))

(deftest test-campaign-routes
  (testing "POST /cia/campaign"
    (let [response (post "cia/campaign"
                         :body {:title "campaign"
                                :description ["description"]
                                :type "anything goes here"
                                :intended_effect ["Theft"]
                                :indicators ["indicator-foo" "indicator-bar"]
                                :timestamp "2016-02-11T00:40:48.212-00:00"
                                :expires "2016-07-11T00:40:48.212-00:00"})
          campaign (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "campaign"
              :description ["description"]
              :type "anything goes here"
              :intended_effect ["Theft"]
              :indicators ["indicator-foo" "indicator-bar"]
              :timestamp #inst "2016-02-11T00:40:48.212-00:00"
              :expires #inst "2016-07-11T00:40:48.212-00:00"}
             (dissoc campaign
                     :id)))

      (testing "GET /cia/campaign/:id"
        (let [response (get (str "cia/campaign/" (:id campaign)))
              campaign (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "campaign"
                  :description ["description"]
                  :type "anything goes here"
                  :intended_effect ["Theft"]
                  :indicators ["indicator-foo" "indicator-bar"]
                  :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                  :expires #inst "2016-07-11T00:40:48.212-00:00"}
                 (dissoc campaign
                         :id)))))

      (testing "DELETE /cia/campaign/:id"
        (let [response (delete (str "cia/campaign/" (:id campaign)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/campaign/" (:id campaign)))]
            (is (= 404 (:status response)))))))))

(deftest test-coa-routes
  (testing "POST /cia/coa"
    (let [response (post "cia/coa"
                         :body {:title "coa"
                                :description ["description"]
                                :type "Eradication"
                                :objective ["foo" "bar"]
                                :timestamp "2016-02-11T00:40:48.212-00:00"})
          coa (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "coa"
              :description ["description"]
              :type "Eradication"
              :objective ["foo" "bar"]
              :timestamp #inst "2016-02-11T00:40:48.212-00:00"}
             (dissoc coa
                     :id)))

      (testing "GET /cia/coa/:id"
        (let [response (get (str "cia/coa/" (:id coa)))
              coa (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "coa"
                  :description ["description"]
                  :type "Eradication"
                  :objective ["foo" "bar"]
                  :timestamp #inst "2016-02-11T00:40:48.212-00:00"}
                 (dissoc coa
                         :id)))))

      (testing "DELETE /cia/coa/:id"
        (let [response (delete (str "/cia/coa/" (:id coa)))]
          (is (= 204 (:status response)))
          (let [response (get (str "/cia/coa/" (:id coa)))]
            (is (= 404 (:status response)))))))))

(deftest test-exploit-target-routes
  (testing "POST /cia/exploit-target"
    (let [response (post "cia/exploit-target"
                         :body {:title "exploit-target"
                                :description ["description"]
                                :vulnerability [{:title "vulnerability"
                                                 :description ["description"]}]
                                :timestamp "2016-02-11T00:40:48.212-00:00"})
          exploit-target (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "exploit-target"
              :description ["description"]
              :vulnerability [{:title "vulnerability"
                               :description ["description"]}]
              :timestamp #inst "2016-02-11T00:40:48.212-00:00"}
             (dissoc exploit-target
                     :id)))

      (testing "GET /cia/exploit-target/:id"
        (let [response (get (str "cia/exploit-target/" (:id exploit-target)))
              exploit-target (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "exploit-target"
                  :description ["description"]
                  :vulnerability [{:title "vulnerability"
                                   :description ["description"]}]
                  :timestamp #inst "2016-02-11T00:40:48.212-00:00"}
                 (dissoc exploit-target
                         :id)))))

      (testing "DELETE /cia/exploit-target/:id"
        (let [response (delete (str "cia/exploit-target/" (:id exploit-target)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/exploit-target/" (:id exploit-target)))]
            (is (= 404 (:status response)))))))))

(deftest test-incident-routes
  (testing "POST /cia/incident"
    (let [response (post "cia/incident"
                         :body {:title "incident"
                                :description ["description"]
                                :confidence "High"
                                :categories ["Denial of Service"
                                             "Improper Usage"]
                                :timestamp "2016-02-11T00:40:48.212-00:00"})
          incident (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "incident"
              :description ["description"]
              :confidence "High"
              :categories ["Denial of Service"
                           "Improper Usage"]
              :timestamp #inst "2016-02-11T00:40:48.212-00:00"}
             (dissoc incident
                     :id)))

      (testing "GET /cia/incident/:id"
        (let [response (get (str "cia/incident/" (:id incident)))
              incident (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "incident"
                  :description ["description"]
                  :confidence "High"
                  :categories ["Denial of Service"
                               "Improper Usage"]
                  :timestamp #inst "2016-02-11T00:40:48.212-00:00"}
                 (dissoc incident
                         :id)))))

      (testing "DELETE /cia/incident/:id"
        (let [response (delete (str "cia/incident/" (:id incident)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/incident/" (:id incident)))]
            (is (= 404 (:status response)))))))))

(deftest test-indicator-routes
  (testing "POST /cia/indicator"
    (let [response (post "cia/indicator"
                         :body {:title "indicator"
                                :description ["description"]
                                :producer "producer"
                                :type ["C2" "IP Watchlist"]
                                :expires "2016-07-11T00:40:48.212-00:00"})
          indicator (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "indicator"
              :description ["description"]
              :producer "producer"
              :type ["C2" "IP Watchlist"]
              :expires #inst "2016-07-11T00:40:48.212-00:00"}
             (dissoc indicator
                     :id)))

      (testing "GET /cia/indicator/:id"
        (let [response (get (str "cia/indicator/" (:id indicator)))
              indicator (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "indicator"
                  :description ["description"]
                  :producer "producer"
                  :type ["C2" "IP Watchlist"]
                  :expires #inst "2016-07-11T00:40:48.212-00:00"}
                 (dissoc indicator
                         :id)))))

      (testing "DELETE /cia/indicator/:id"
        (let [response (delete (str "cia/indicator/" (:id indicator)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/indicator/" (:id indicator)))]
            (is (= 404 (:status response)))))))))

(deftest test-judgement-routes
  (testing "POST /cia/judgement"
    (let [response (post "cia/judgement"
                         :body {:indicators []
                                :observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :timestamp "2016-02-11T00:40:48.212-00:00"})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:observable {:value "1.2.3.4"
                           :type "ip"}
              :indicators []
              :disposition 2
              :priority 100
              :severity 100
              :confidence "Low"
              :source "test"
              :timestamp #inst "2016-02-11T00:40:48.212-00:00"}
             (dissoc judgement
                     :id)))

      (testing "GET /cia/judgement/:id"
        (let [response (get (str "cia/judgement/" (:id judgement)))
              judgement (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:observable {:value "1.2.3.4"
                               :type "ip"}
                  :indicators []
                  :disposition 2
                  :priority 100
                  :severity 100
                  :confidence "Low"
                  :source "test"
                  :timestamp #inst "2016-02-11T00:40:48.212-00:00"}
                 (dissoc judgement
                         :id)))))

      (testing "DELETE /cia/judgement/:id"
        (let [temp-judgement (-> (post "cia/judgement"
                                       :body {:indicators []
                                              :observable {:value "9.8.7.6"
                                                           :type "ip"}
                                              :disposition 3
                                              :source "test"
                                              :priority 100
                                              :severity 100
                                              :confidence "Low"
                                              :timestamp "2016-02-11T00:40:48.212-00:00"})
                                 :parsed-body)
              response (delete (str "cia/judgement/" (:id temp-judgement)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/judgement/" (:id temp-judgement)))]
            (is (= 404 (:status response))))))

      (testing "POST /cia/judgement/:id/feedback"
        (let [response (post (str "cia/judgement/" (:id judgement) "/feedback")
                             :body {:feedback -1
                                    :reason "false positive"})
              feedback (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:judgement (:id judgement),
                  :feedback -1,
                  :reason "false positive"}
                 (dissoc feedback
                         :id
                         :timestamp))))

        (testing "GET /cia/judgement/:id/feedback"
          ;; create some more feedbacks
          (let [response (post "cia/judgement"
                               :body {:indicators []
                                      :observable {:value "4.5.6.7"
                                                   :type "ip"}
                                      :disposition 1
                                      :source "test"})
                another-judgement (:parsed-body response)]
            (post (str "cia/judgement/" (:id another-judgement) "/feedback")
                  :body {:feedback 0
                         :reason "yolo"}))
          (post (str "cia/judgement/" (:id judgement) "/feedback")
                :body {:feedback 1
                       :reason "true positive"})

          (let [response (get (str "cia/judgement/" (:id judgement) "/feedback"))
                feedbacks (:parsed-body response)]
            (is (= 200 (:status response)))
            (is (= [{:judgement (:id judgement),
                     :feedback -1,
                     :reason "false positive"}
                    {:judgement (:id judgement),
                     :feedback 1,
                     :reason "true positive"}]
                   (map #(dissoc % :id :timestamp)
                        feedbacks)))))))))

(deftest test-observable-judgements-route

  (testing "test setup: create a judgement (1)"
    (let [response (post "cia/judgement"
                         :body {:indicators []
                                :observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :timestamp "2016-02-12T00:00:00.000-00:00"})]
      (is (= 200 (:status response)))))
  (testing "test setup: create a judgement (2)"
    (let [response (post "cia/judgement"
                         :body {:indicators []
                                :observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :timestamp "2016-02-12T00:00:00.000-00:00"})]
      (is (= 200 (:status response)))))
  (testing "test setup: create a judgement (3)"
    (let [response (post "cia/judgement"
                         :body {:indicators []
                                :observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 1
                                :source "test"
                                :priority 50
                                :severity 60
                                :confidence "High"
                                :timestamp "2016-02-11T00:00:00.000-00:00"})]
      (is (= 200 (:status response)))))

  (testing "GET /cia/:observable_type/:observable_value/judgements"
    (let [response (get "cia/ip/10.0.0.1/judgements")
          judgements (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= [{:indicators []
               :observable {:value "10.0.0.1"
                            :type "ip"}
               :disposition 2
               :source "test"
               :priority 100
               :severity 100
               :confidence "Low"
               :timestamp #inst "2016-02-12T00:00:00.000"}
              {:indicators []
               :observable {:value "10.0.0.1"
                            :type "ip"}
               :disposition 1
               :source "test"
               :priority 50
               :severity 60
               :confidence "High"
               :timestamp #inst "2016-02-11T00:00:00.000-00:00"}]
             (->> judgements
                  (map #(dissoc % :id))))))))

(deftest test-observable-indicators-and-sightings-routes

  (testing "test setup: create an indicator (1)"
    (let [response (post "cia/indicator"
                         :body {:title "indicator"
                                :observable {:value "1.2.3.4"
                                             :type "ip"}
                                :sightings [{:timestamp "2016-02-01T00:00:00.000-00:00"
                                             :source {:description "foo"}
                                             :confidence "Medium"}
                                            {:timestamp "2016-02-01T12:00:00.000-00:00"
                                             :source {:description "bar"}
                                             :confidence "High"}]
                                :description ["description"]
                                :producer "producer"
                                :type ["C2" "IP Watchlist"]
                                :expires "2016-02-12T00:00:00.000-00:00"})]
      (is (= 200 (:status response)))))
  (testing "test setup: create an indicator (2)"
    (let [response (post "cia/indicator"
                         :body {:title "indicator"
                                :observable {:value "10.0.0.1"
                                             :type "ip"}
                                :sightings [{:timestamp "2016-02-04T12:00:00.000-00:00"
                                             :source {:description "spam"}
                                             :confidence "None"}]
                                :description ["description"]
                                :producer "producer"
                                :type ["C2" "IP Watchlist"]
                                :expires "2016-02-12T00:00:00.000-00:00"})]
      (is (= 200 (:status response)))))
  (testing "test setup: create an indicator (1)"
    (let [response (post "cia/indicator"
                         :body {:title "indicator"
                                :observable {:value "10.0.0.1"
                                             :type "ip"}
                                :sightings [{:timestamp "2016-02-05T01:00:00.000-00:00"
                                             :source {:description "foo"}
                                             :confidence "High"}
                                            {:timestamp "2016-02-05T02:00:00.000-00:00"
                                             :source {:description "bar"}
                                             :confidence "Low"}]
                                :description ["description"]
                                :producer "producer"
                                :type ["C2" "IP Watchlist"]
                                :expires "2016-02-11T00:00:00.000-00:00"})]
      (is (= 200 (:status response)))))

  (testing "GET /cia/:observable_type/:observable_value/indicators"
    (let [response (get "cia/ip/10.0.0.1/indicators")
          indicators (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= [{:title "indicator"
               :observable {:value "10.0.0.1"
                            :type "ip"}
               :sightings [{:timestamp #inst "2016-02-04T12:00:00.000-00:00"
                            :source {:description "spam"}
                            :confidence "None"}]
               :description ["description"]
               :producer "producer"
               :type ["C2" "IP Watchlist"]
               :expires #inst "2016-02-12T00:00:00.000-00:00"}
              {:title "indicator"
               :observable {:value "10.0.0.1"
                            :type "ip"}
               :sightings [{:timestamp #inst "2016-02-05T01:00:00.000-00:00"
                            :source {:description "foo"}
                            :confidence "High"}
                           {:timestamp #inst "2016-02-05T02:00:00.000-00:00"
                            :source {:description "bar"}
                            :confidence "Low"}]
               :description ["description"]
               :producer "producer"
               :type ["C2" "IP Watchlist"]
               :expires #inst "2016-02-11T00:00:00.000-00:00"}]
             (->> indicators
                  (map #(dissoc % :id)))))))

  (testing "GET /cia/:observable_type/:observable_value/sightings"
    (let [response (get "cia/ip/10.0.0.1/sightings")
          sightings (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= [{:timestamp #inst "2016-02-04T12:00:00.000-00:00"
               :source {:description "spam"}
               :confidence "None"}
              {:timestamp #inst "2016-02-05T01:00:00.000-00:00"
               :source {:description "foo"}
               :confidence "High"}
              {:timestamp #inst "2016-02-05T02:00:00.000-00:00"
               :source {:description "bar"}
               :confidence "Low"}]
             sightings)))))

(deftest test-ttp-routes
  (testing "POST /cia/ttp"
    (let [response (post "cia/ttp"
                         :body {:title "ttp"
                                :description ["description"]
                                :type "foo"
                                :indicators ["indicator-1" "indicator-2"]
                                :timestamp "2016-02-11T00:40:48.212-00:00"
                                :expires "2016-07-11T00:40:48.212-00:00"})
          ttp (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "ttp"
              :description ["description"]
              :type "foo"
              :indicators ["indicator-1" "indicator-2"]
              :timestamp #inst "2016-02-11T00:40:48.212-00:00"
              :expires #inst "2016-07-11T00:40:48.212-00:00"}
             (dissoc ttp
                     :id)))

      (testing "GET /cia/ttp/:id"
        (let [response (get (str "cia/ttp/" (:id ttp)))
              ttp (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "ttp"
                  :description ["description"]
                  :type "foo"
                  :indicators ["indicator-1" "indicator-2"]
                  :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                  :expires #inst "2016-07-11T00:40:48.212-00:00"}
                 (dissoc ttp
                         :id)))))

      (testing "DELETE /cia/ttp/:id"
        (let [response (delete (str "cia/ttp/" (:id ttp)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/ttp/" (:id ttp)))]
            (is (= 404 (:status response)))))))))
