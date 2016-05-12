(ns ctia.http.routes.indicator-test
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [clojure.tools.logging :as log]
            [ctia.schemas
             [campaign :refer [NewCampaign]]
             [coa :refer [NewCOA]]
             [indicator :refer [NewIndicator]]
             [judgement :refer [NewJudgement]]
             [sighting :refer [NewSighting]]
             [ttp :refer [NewTTP]]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [http :refer [api-key test-get-list test-post assert-post]]
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

(def new-indicator
  {
   :description "new indicator"
   :producer "yLmE7o]N"
   :type "indicator"
   :short_description "ind"
   :alternate_ids ["^"]
   :title "New Indicator"
   :version "0.0.0"
   :negate false
   :confidence "Unknown"})

(deftest-for-each-store test-sightings-from-indicator
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (let [sightings-seed [{:description ""
                         :source_device "network.proxy"
                         :observables [{:value "google.com"
                                        :type "domain"}]
                         :type "sighting"
                         :tlp "white"
                         :timestamp "1970-01-01T00:00:00.000-00:00"
                         :confidence "Low"}
                        {:description "wrong mail"
                         :source_device "network"
                         :observables [{:value "wu@wang.cn"
                                        :type "email"}]
                         :type "sighting"
                         :timestamp "2016-01-01T00:00:00.000-00:00"
                         :tlp "white"
                         :confidence "Low"}
                        {:description ""
                         :source_device "process.sandbox"
                         :observables [{:value "P"
                                        :type "imei"}]
                         :type "sighting"
                         :timestamp "2016-05-11T03:04:10.000-03:00"
                         :reference "GL"
                         :tlp "yellow"
                         :confidence "Unknown"}]]
    (testing "POST /ctia/indicator"
      (when-let [indicator (test-post "ctia/indicator" new-indicator)]
        (testing "POST /ctia/sighting"
          (let [new-sightings (map #(into % {:indicators
                                             [{:indicator_id (:id indicator)}]})
                                   sightings-seed)
                sightings (doall (map #(assert-post "ctia/sighting" %) new-sightings))]
            (testing "GET /ctia/indicator/:id/sightings"
              (test-get-list (str "ctia/indicator/" (url-encode (:id indicator)) "/sightings")
                             sightings))))))))

(deftest-for-each-store test-campaigns-from-indicator
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (let [new-campaigns [{:title "campaign-1"
                        :description "1st campaign"
                        :valid_time {:start_time "1970-01-01T00:00:00.000-00:00"
                                     :end_time "1970-01-01T00:00:00.000-00:00"}
                        :indicators []
                        :campaign_type ""
                        :tlp "white"}
                       {:title "campaign-2"
                        :description "2nd campaign"
                        :valid_time {:start_time "1970-01-02T00:00:00.000-00:00"
                                     :end_time "1970-01-02T00:00:00.000-00:00"}
                        :indicators []
                        :campaign_type ""
                        :tlp "red"}
                       {:title "campaign-3"
                        :description "3rd campaign"
                        :valid_time {:start_time "1970-01-03T00:00:00.000-00:00"
                                     :end_time "1970-01-03T00:00:00.000-00:00"}
                        :indicators []
                        :campaign_type ""
                        :tlp "green"}
                       {:title "campaign-4"
                        :description "4th campaign"
                        :valid_time {:start_time "1970-01-04T00:00:00.000-00:00"
                                     :end_time "1970-01-04T00:00:00.000-00:00"}
                        :indicators []
                        :campaign_type ""
                        :tlp "green"}]
        all-campaigns (doall (map #(assert-post "ctia/campaign" %) new-campaigns))
        ;; take only half created campaign
        ;; to verify we select only the relevant campaigns
        campaigns (take (/ (count new-campaigns) 2) all-campaigns)
        campaign-ids (map :id campaigns)]
    (let [related-campaign-bloc {:related_campaigns
                                 (map (fn [c] {:campaign_id c}) campaign-ids)}
          new-indicator-1 (into new-indicator related-campaign-bloc)
          indicator (test-post "ctia/indicator" new-indicator-1)]
      (test-get-list (str "ctia/indicator/"
                          (url-encode (:id indicator))
                          "/campaigns")
                     campaigns))))

(deftest-for-each-store test-coas-from-indicator
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (let [new-coas [{:title "coa-1"
                   :description "1st COA"
                   :valid_time {:start_time #inst "1970-01-01T00:00:00.000-00:00"
                                :end_time #inst "1970-01-01T00:00:00.000-00:00"}
                   :stage "Response"
                   :efficacy "Medium"
                   :type "COA"
                   :coa_type "Diplomatic Actions"
                   :tlp "white"
                   :cost "Low"}
                  {:title "coa-2"
                   :description "2nd COA"
                   :valid_time {:start_time #inst "1970-01-02T00:00:00.000-00:00"
                                :end_time #inst "1970-01-02T00:00:00.000-00:00"}
                   :stage "Response"
                   :efficacy "Medium"
                   :type "COA"
                   :coa_type "Diplomatic Actions"
                   :tlp "white"
                   :cost "Low"}
                  {:title "coa-3"
                   :description "3rd COA"
                   :valid_time {:start_time #inst "1970-01-03T00:00:00.000-00:00"
                                :end_time #inst "1970-01-03T00:00:00.000-00:00"}
                   :stage "Response"
                   :efficacy "Medium"
                   :type "COA"
                   :coa_type "Diplomatic Actions"
                   :tlp "white"
                   :cost "Low"}
                  {:title "coa-4"
                   :description "4th COA"
                   :valid_time {:start_time #inst "1970-01-04T00:00:00.000-00:00"
                                :end_time #inst "1970-01-04T00:00:00.000-00:00"}
                   :stage "Response"
                   :efficacy "Medium"
                   :type "COA"
                   :coa_type "Diplomatic Actions"
                   :tlp "white"
                   :cost "Low"}]
        all-coas (doall (map #(assert-post "ctia/coa" %) new-coas))
        ;; take only half created coa
        ;; to verify we select only the relevant coas
        coas (take (/ (count new-coas) 2) all-coas)
        coa-ids (map :id coas)]
    (let [related-coa-bloc {:related_COAs
                                 (map (fn [c] {:COA_id c}) coa-ids)}
          new-indicator-1 (into new-indicator related-coa-bloc)
          indicator (test-post "ctia/indicator" new-indicator-1)]
      (test-get-list (str "ctia/indicator/"
                          (url-encode (:id indicator))
                          "/coas")
                     coas))))

(deftest-for-each-store test-ttps-from-indicator
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (let [new-ttps [{:title "ttp-1"
                   :description "1st TTP"
                   :valid_time {:start_time #inst "1970-01-01T00:00:00.000-00:00"
                                :end_time #inst "1970-01-01T00:00:00.000-00:00"}
                   :intended_effect "Theft - Intellectual Property"
                   :tlp "green"
                   :ttp_type ""
                   :indicators []}
                  {:title "ttp-2"
                   :description "2nd TTP"
                   :valid_time {:start_time #inst "1970-01-02T00:00:00.000-00:00"
                                :end_time #inst "1970-01-02T00:00:00.000-00:00"}
                   :intended_effect "Theft - Intellectual Property"
                   :tlp "green"
                   :ttp_type ""
                   :indicators []}
                  {:title "ttp-3"
                   :description "3rd TTP"
                   :valid_time {:start_time #inst "1970-01-03T00:00:00.000-00:00"
                                :end_time #inst "1970-01-03T00:00:00.000-00:00"}
                   :intended_effect "Advantage - Economic"
                   :tlp "green"
                   :ttp_type ""
                   :indicators []}
                  {:title "ttp-4"
                   :description "4th TTP"
                   :valid_time {:start_time #inst "1970-01-04T00:00:00.000-00:00"
                                :end_time #inst "1970-01-04T00:00:00.000-00:00"}
                   :intended_effect "Advantage - Economic"
                   :tlp "green"
                   :ttp_type ""
                   :indicators []}]
        all-ttps (doall (map #(assert-post "ctia/ttp" %) new-ttps))
        ;; take only half created ttp
        ;; to verify we select only the relevant ttps
        ttps (take (/ (count new-ttps) 2) all-ttps)
        ttp-ids (map :id ttps)]
    (let [related-ttp-bloc {:indicated_TTP
                            (map (fn [c] {:ttp_id c}) ttp-ids)}
          new-indicator-1 (into new-indicator related-ttp-bloc)
          indicator (test-post "ctia/indicator" new-indicator-1)]
      (test-get-list (str "ctia/indicator/"
                          (url-encode (:id indicator))
                          "/ttps")
                     ttps))))

(deftest-for-each-store test-judgements-from-indicator
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (let [new-judgements [{:valid_time {:start_time #inst "1970-01-01T00:00:00.000-00:00"
                                      :end_time #inst "1970-01-01T00:00:00.000-00:00"}
                         :observable {:value ""
                                      :type "amp-device"}
                         :type "judgement"
                         :source ""
                         :disposition 5
                         :disposition_name "Unknown"
                         :indicators []
                         :source_uri ""
                         :priority 0
                         :severity 0
                         :tlp "red"
                         :confidence "High"}
                        {:valid_time {:start_time #inst "1970-01-01T00:00:00.000-00:00"
                                      :end_time #inst "1969-12-31T23:59:59.999-00:00"}
                         :observable {:value ""
                                      :type "domain"}
                         :reason_uri ""
                         :type "judgement"
                         :source "`"
                         :disposition 5
                         :reason ""
                         :indicators []
                         :source_uri "E"
                         :disposition_name "Unknown"
                         :priority 0N
                         :severity -1
                         :tlp "yellow"
                         :confidence "Low"}
                        {:valid_time {:start_time #inst "1970-01-01T00:00:00.000-00:00"
                                      :end_time #inst "1970-01-01T00:00:00.002-00:00"}
                         :observable {:value ""
                                      :type "domain"}
                         :reason_uri ""
                         :type "judgement"
                         :source "$B"
                         :disposition 5
                         :reason "ED"
                         :indicators []
                         :source_uri "mU"
                         :disposition_name "Unknown"
                         :priority -2
                         :severity -1
                         :tlp "red"
                         :confidence "Medium"}]
        all-judgements (doall (map #(assert-post "ctia/judgement" %) new-judgements))
        ;; take only half created judgement
        ;; to verify we select only the relevant judgements
        judgements (take (/ (count new-judgements) 2) all-judgements)
        judgement-ids (map :id judgements)]
    (let [related-judgement-bloc {:judgements
                            (map (fn [c] {:judgement_id c}) judgement-ids)}
          new-indicator-1 (into new-indicator related-judgement-bloc)
          indicator (test-post "ctia/indicator" new-indicator-1)]
      (test-get-list (str "ctia/indicator/"
                          (url-encode (:id indicator))
                          "/judgements")
                     judgements))))
