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
                                :confidence "High"})
          actor (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:description ["description"],
              :type "Hacker",
              :title "actor",
              :confidence "High",
              :source {:description "a source"}}
             (dissoc actor
                     :id
                     :timestamp
                     :expires)))

      (testing "GET /cia/actor/:id"
        (let [response (get (str "cia/actor/" (:id actor)))
              actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:description ["description"],
                  :type "Hacker",
                  :title "actor",
                  :confidence "High",
                  :source {:description "a source"}}
                 (dissoc actor
                         :id
                         :timestamp
                         :expires)))))

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
                                :indicators ["indicator-foo" "indicator-bar"]})
          campaign (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:title "campaign"
              :description ["description"]
              :type "anything goes here"
              :intended_effect ["Theft"]
              :indicators ["indicator-foo" "indicator-bar"]}
             (dissoc campaign
                     :id
                     :timestamp
                     :expires)))

      (testing "GET /cia/campaign/:id"
        (let [response (get (str "cia/campaign/" (:id campaign)))
              campaign (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:title "campaign"
                  :description ["description"]
                  :type "anything goes here"
                  :intended_effect ["Theft"]
                  :indicators ["indicator-foo" "indicator-bar"]}
                 (dissoc campaign
                         :id
                         :timestamp
                         :expires)))))

      (testing "DELETE /cia/campaign/:id"
        (let [response (delete (str "cia/campaign/" (:id campaign)))]
          (is (= 204 (:status response)))
          (let [response (get (str "cia/campaign/" (:id campaign)))]
            (is (= 404 (:status response)))))))))

(deftest test-judgement-routes
  (testing "POST /cia/judgement"
    (let [response (post "cia/judgement"
                         :body {:indicators []
                                :observable {:value "1.2.3.4"
                                             :type "ip"}
                                :disposition 2
                                :source "test"})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= {:observable {:value "1.2.3.4", :type "ip"},
              :indicators [],
              :disposition 2,
              :priority 100,
              :severity 100,
              :confidence "Low",
              :source "test"}
             (dissoc judgement
                     :id
                     :timestamp)))

      (testing "GET /cia/judgement/:id"
        (let [response (get (str "cia/judgement/" (:id judgement)))
              judgement (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:observable {:value "1.2.3.4", :type "ip"},
                  :indicators [],
                  :disposition 2,
                  :priority 100,
                  :severity 100,
                  :confidence "Low",
                  :source "test"}
                 (dissoc judgement
                         :id
                         :timestamp)))))

      (testing "DELETE /cia/judgement/:id"
        (let [temp-judgement (-> (post "cia/judgement"
                                       :body {:indicators []
                                              :observable {:value "9.8.7.6"
                                                           :type "ip"}
                                              :disposition 3
                                              :source "test"})
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
