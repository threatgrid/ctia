(ns ctia.http.routes.indicator-test
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [clojure.tools.logging :as log]
            [ctia.schemas
             [campaign :refer [NewCampaign]]
             [coa :refer [NewCOA]]
             [indicator :refer [NewIndicator]]
             [sighting :refer [NewSighting]]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [http :refer [api-key test-get-list test-post]]
             [store :refer [deftest-for-each-store]]]
            [ring.util.codec :refer [url-encode]]
            [schema-generators
             [complete :as c]
             [generators :as g]]))

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
            :tlp "green"
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
                          :tlp "yellow"
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
                :tlp "yellow"
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

(deftest-for-each-store test-sightings-from-indicator
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (let [new-indicators (g/sample 10 NewIndicator)
        ;; BEWARE ES AS A MAXIMUM TO 10 !!!!!!
        nb-sightings 10]
    (if (> nb-sightings ctia.lib.es.document/default-limit)
      (log/error
       "BEWARE! ES Couldn't handle more than 10 element by search by default."
       "It is set to " ctia.lib.es.document/default-limit " in `lib.es.document.clj`"
       "You might want to change either `nb-sightings` in this test"
       "or change `ctia.lib.es.document/default-limit`"))

    (doseq [new-indicator new-indicators]
      (testing "POST /ctia/indicator"
        (when-let [indicator (test-post "ctia/indicator" new-indicator)]
          (testing "POST /ctia/sighting"
            (let [new-sightings (->> (g/sample nb-sightings NewSighting)
                                     (map #(dissoc % :relations)) ;; s/Any generator are tricky
                                     (map #(into % {:indicators
                                                    [{:indicator_id (:id indicator)}]})))
                  sightings (doall (map #(test-post "ctia/sighting" %)
                                        new-sightings))
                  sighting-ids (map :id sightings)]
              (when-not (empty? (remove nil? sightings))
                (testing "GET /ctia/indicator/:id/sightings"
                  (let [search-resp (get (str "ctia/indicator/" (url-encode (:id indicator)) "/sightings")
                                         :headers {"api_key" api-key})]
                    (is (= 200 (:status search-resp)))
                    (is (= (set sighting-ids)
                           (set (map :id (:parsed-body search-resp)))))))))))))))


(deftest-for-each-store test-campaigns-from-indicator
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (let [new-indicators (g/sample 10 NewIndicator)
        nb-campaigns 10]
    (if (> nb-campaigns ctia.lib.es.document/default-limit)
      (log/error
       "BEWARE! ES Couldn't handle more than 10 element by search by default."
       "It is set to " ctia.lib.es.document/default-limit " in `lib.es.document.clj`"
       "You might want to change either `nb-campaigns` in this test"
       "or change `ctia.lib.es.document/default-limit`"))
    (let [new-campaigns (g/sample nb-campaigns NewCampaign)
          campaigns (remove nil? (map #(test-post "ctia/campaign" %)
                                      new-campaigns))
          campaign-ids (map :id campaigns)]
      (is (not (empty? campaigns)))
      (when-not (empty? campaigns)
        (let [related-campaign-bloc {:related_campaigns
                                     (map (fn [c] {:campaign_id c}) campaign-ids)}
              new-indicator (c/complete related-campaign-bloc
                                        NewIndicator)
              indicator (test-post "ctia/indicator" new-indicator)]
          (test-get-list (str "ctia/indicator/"
                              (url-encode (:id indicator))
                              "/campaigns")
                         campaigns))))))

(deftest-for-each-store test-coas-from-indicator
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (let [new-indicators (g/sample 10 NewIndicator)
        nb-coas 10]
    (if (> nb-coas ctia.lib.es.document/default-limit)
      (log/error
       "BEWARE! ES Couldn't handle more than 10 element by search by default."
       "It is set to " ctia.lib.es.document/default-limit " in `lib.es.document.clj`"
       "You might want to change either `nb-coas` in this test"
       "or change `ctia.lib.es.document/default-limit`"))
    (let [new-coas (g/sample nb-coas NewCOA)
          coas (remove nil? (map #(test-post "ctia/coa" %)
                                      new-coas))
          coa-ids (map :id coas)]
      (is (not (empty? coas)))
      (when-not (empty? coas)
        (let [related-coa-bloc {:related_COAs
                                     (map (fn [c] {:COA_id c}) coa-ids)}
              new-indicator (c/complete related-coa-bloc
                                        NewIndicator)
              indicator (test-post "ctia/indicator" new-indicator)]
          (test-get-list (str "ctia/indicator/"
                              (url-encode (:id indicator))
                              "/coas")
                         coas))))))
