(ns ctia.http.handler.stix12-test
  (:require [clj-http.client :as http]
            [clojure.data.xml :as xml]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.test-helpers.core :as helpers :refer [post]]
            [ctia.http.handler :as handler]
            [stencil.core :refer [render-file]])
  (:import java.io.StringReader))

(use-fixtures :once helpers/fixture-properties)

(use-fixtures :each (join-fixtures [(helpers/fixture-server handler/app)
                                    helpers/fixture-schema-validation
                                    helpers/fixture-allow-all-auth
                                    helpers/fixture-in-memory-store]))

(deftest test-stix12-indicator-routes
  (testing "GET /ctia/stix12/indicator/:id"
    (let [{indicator-1-status :status
           indicator-1 :parsed-body}
          (post "ctia/indicator"
                :body {:title "indicator 1"
                       :description "a minimal indicator"
                       :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                    :end_time "2016-07-11T00:40:48.212-00:00"}
                       :producer "producer"
                       :observable {:type "ip"
                                    :value "1.2.3.4"}})

          {indicator-2-status :status
           indicator-2 :parsed-body}
          (post "ctia/indicator"
                :body {:title "indicator 2"
                       :description "a minimal indicator"
                       :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                    :end_time "2016-07-11T00:40:48.212-00:00"}
                       :producer "producer"
                       :observable {:type "ip"
                                    :value "1.2.3.4"}})

          {indicator-3-status :status
           indicator-3 :parsed-body
           :as response}
          (post "ctia/indicator"
                :body {:title "indicator 3"
                       :description "a minimal indicator"
                       :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                    :end_time "2016-07-11T00:40:48.212-00:00"}
                       :producer "producer"
                       :observable {:type "ip"
                                    :value "1.2.3.4"}})

          {indicator-4-status :status
           indicator-4 :parsed-body}
          (post "ctia/indicator"
                :body {:title "indicator 4"
                       :description "a mostly complete indicator"
                       :short_description "short description"
                       :alternate_ids ["foo" "bar"]
                       :negate false
                       :version 1
                       :producer "producer"
                       :tags ["spam" "eggs"]
                       :type ["C2" "IP Watchlist"]
                       :observable {:type "ip"
                                    :value "1.2.3.4"}
                       :related_indicators [{:indicator_id (:id indicator-1)
                                             :confidence "High"
                                             :source "source"
                                             :relationship "relationship"}]
                       :composite_indicator_expression
                       {:operator "and"
                        :indicators [(:id indicator-2)
                                     (:id indicator-3)]}
                       :likely_impact "likely impact"
                       :confidence "High"
                       :valid_time {:start_time "2016-05-11T00:40:48.212-00:00"
                                    :end_time "2016-07-11T00:40:48.212-00:00"}
                       :kill_chain_phases ["phase 1" "phase 2" "phase 3"]
                       :related_campaigns [{:confidence "High"
                                            :source "source"
                                            :relationship "relationship"
                                            :campaign_id "campaign-123"}]
                       :related_COAs [{:confidence "High"
                                       :source "source"
                                       :relationship "relationship"
                                       :COA_id "coa-123"}]
                       :judgements [{:judgement_id "judgement-123"}
                                    {:judgement_id "judgement-234"}]})]
      (is (= 200 indicator-1-status))
      (is (= 200 indicator-2-status))
      (is (= 200 indicator-3-status))
      (is (= 200 indicator-4-status))

      (let [{status :status
             indicator :body}
            (http/get (helpers/url (str "ctia/stix12/indicator/"
                                        (:id indicator-4)))
                      {:throw-exceptions false})]
        ;; Hint: use gui-diff to compare (add it to your ~/.lein/profiles.clj)
        ;;       using a little REPL magic, or print out both XMLs below and
        ;;       diff externally
        (is (= 200 status))
        (is (= (xml/parse-str indicator)
               (xml/parse-str
                (render-file "indicator.xml"
                             {:indicator-1-id (:id indicator-1)
                              :indicator-2-id (:id indicator-2)
                              :indicator-3-id (:id indicator-3)
                              :indicator-4-id (:id indicator-4)}))))))))
