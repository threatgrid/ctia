(ns ctia.http.routes.observable.verdict.es.verdict-expired-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.lib.time :as time]
            [clj-momo.test-helpers.core :as mht]
            [clojure.test :refer [is deftest testing use-fixtures]]
            [ctia.properties :as props]
            [ctia.http.routes.observable.verdict.es.core :refer :all]
            [ctia.test-helpers
             [core :as helpers :refer [get]]
             [es :as es-helpers :refer [post-all-to-es]]]
            [ctim.domain.id :as id]))

(use-fixtures :once
  mht/fixture-schema-validation
  helpers/fixture-properties:clean
  helpers/fixture-properties:events-enabled
  helpers/fixture-allow-all-auth
  es-helpers/fixture-properties:es-store
  helpers/fixture-ctia
  es-helpers/fixture-delete-store-indexes)

(use-fixtures :each
  es-helpers/fixture-delete-store-indexes)

(deftest test-when-not-expired

  (testing "When there is a valid, not-expired verdict, it is returned by the API"

    (post-all-to-es
     [{:valid_time
       {:start_time (two-months-ago)
        :end_time (in-the-year-2525)},
       :schema_version "0.4.3",
       :observable {:value "biz.baz.net", :type "domain"},
       :reason_uri "https://feed.example.com/12345",
       :type "judgement",
       :created (two-months-ago)
       :source "Uber awesome feed thingy",
       :external_ids ["judgement-1"],
       :disposition 2,
       :reason "Something bad happened",
       :source_uri "https://feed.example.com/12345/source",
       :disposition_name "Malicious",
       :priority 90,
       :id "judgement-b64b635f-eda3-4c9c-bb46-e11dc361066f"
       :severity "High",
       :tlp "green",
       :confidence "High",
       :owner "Unknown"}
      {:valid_time
       {:start_time (two-months-ago)
        :end_time (in-the-year-2525)},
       :schema_version "0.4.3",
       :observable {:value "biz.baz.net", :type "domain"},
       :type "verdict",
       :created (two-months-ago)
       :disposition 2,
       :disposition_name "Malicious",
       :id "verdict-7c1787bb-592c-4a3e-a9c8-05f3a640869d",
       :judgement_id "judgement-b64b635f-eda3-4c9c-bb46-e11dc361066f"}
      {:valid_time
       {:start_time (one-week-ago)
        :end_time (in-the-year-2525)},
       :schema_version "0.4.3",
       :observable {:value "biz.baz.net", :type "domain"},
       :reason_uri "https://feed.example.com/12345",
       :type "judgement",
       :created (one-week-ago)
       :source "Uber awesome feed thingy",
       :external_ids ["judgement-2"],
       :disposition 2,
       :reason "Something bad happened",
       :source_uri "https://feed.example.com/12345/source",
       :disposition_name "Malicious",
       :priority 90,
       :id "judgement-3f5ed621-087f-4bb3-b175-baa64b5d3d9b",
       :severity "High",
       :tlp "green",
       :confidence "High",
       :owner "Unknown"}])

    (let [{status :status
           verdict :parsed-body}
          (get "ctia/domain/biz.baz.net/verdict")]
      (is (= 200 status))
      (is (= {:valid_time
              {:start_time (time/timestamp
                            (two-months-ago))
               :end_time (time/timestamp
                          (in-the-year-2525))},
              :observable {:value "biz.baz.net",
                           :type "domain"},
              :type "verdict",
              :disposition 2,
              :disposition_name "Malicious",
              :judgement_id (id/short-id->long-id
                             "judgement-b64b635f-eda3-4c9c-bb46-e11dc361066f"
                             props/get-http-show)}
             (dissoc verdict :id))))))

(deftest test-expired-judgement-and-verdict-with-alternative-judgement

  (testing (str "When a verdict that became expired has a backup judgement,"
                "the API creates a new verdict in the handler")
    (post-all-to-es
     [{:valid_time
       {:start_time (two-months-ago)
        :end_time (one-day-ago)},
       :schema_version "0.4.3",
       :observable {:value "foo.bar.net", :type "domain"},
       :reason_uri "https://feed.example.com/12345",
       :type "judgement",
       :created (two-months-ago)
       :source "Uber awesome feed thingy",
       :external_ids ["judgement-1"],
       :disposition 2,
       :reason "Something bad happened",
       :source_uri "https://feed.example.com/12345/source",
       :disposition_name "Malicious",
       :priority 90,
       :id "judgement-55164e5b-485b-44eb-8be3-302b03b79e11"
       :severity "High",
       :tlp "green",
       :confidence "High",
       :owner "Unknown"}
      {:valid_time
       {:start_time (one-week-ago)
        :end_time (in-the-year-2525)},
       :schema_version "0.4.3",
       :observable {:value "foo.bar.net", :type "domain"},
       :reason_uri "https://feed.example.com/12345",
       :type "judgement",
       :created (one-week-ago)
       :source "Uber awesome feed thingy",
       :external_ids ["judgement-2"],
       :disposition 2,
       :reason "Something bad happened",
       :source_uri "https://feed.example.com/12345/source",
       :disposition_name "Malicious",
       :priority 90,
       :id "judgement-3a366c93-2541-4670-b516-23088904b4bd",
       :severity "High",
       :tlp "green",
       :confidence "High",
       :owner "Unknown"}
      {:valid_time
       {:start_time (two-weeks-ago)
        :end_time (one-week-ago)} ;; expired
       :schema_version "0.4.3",
       :observable {:value "foo.bar.net", :type "domain"},
       :reason_uri "https://feed.example.com/12345",
       :type "judgement",
       :created (two-weeks-ago)
       :source "Uber awesome feed thingy",
       :external_ids ["judgement-3"],
       :disposition 2,
       :reason "Something bad happened",
       :source_uri "https://feed.example.com/12345/source",
       :disposition_name "Malicious",
       :priority 100 ;; Highest priority
       :id "judgement-6750947a-2c40-494f-aff9-cd04a1b264ef"
       :severity "High",
       :tlp "green",
       :confidence "High",
       :owner "Unknown"}
      {:valid_time
       {:start_time (two-months-ago)
        :end_time (one-day-ago)},
       :schema_version "0.4.3",
       :observable {:value "foo.bar.net", :type "domain"},
       :type "verdict",
       :created (two-months-ago)
       :disposition 2,
       :disposition_name "Malicious",
       :id "verdict-07c70191-324a-4e13-8a9e-23d18fbd55c7",
       :judgement_id "judgement-55164e5b-485b-44eb-8be3-302b03b79e11"}])

    (let [{status :status
           verdict :parsed-body}
          (get "ctia/domain/foo.bar.net/verdict")]
      (is (= 200 status))
      (is (= {:valid_time
              {:start_time (time/timestamp
                            (one-week-ago))
               :end_time (time/timestamp
                          (in-the-year-2525))},
              :observable {:value "foo.bar.net",
                           :type "domain"},
              :type "verdict",
              :disposition 2,
              :disposition_name "Malicious",
              :judgement_id (id/short-id->long-id
                             "judgement-3a366c93-2541-4670-b516-23088904b4bd"
                             props/get-http-show)}
             (dissoc verdict :id))))))

(deftest test-expired-judgements-and-verdicts-with-no-valid-judgement

  (testing "When everything is expired, there should be no verdict"
    (post-all-to-es
     [{:valid_time
       {:start_time (two-months-ago)
        :end_time (one-month-ago)},
       :schema_version "0.4.3",
       :observable {:value "spam.eggs.net", :type "domain"},
       :reason_uri "https://feed.example.com/12345",
       :type "judgement",
       :created (two-months-ago)
       :source "Uber awesome feed thingy",
       :external_ids ["judgement-1"],
       :disposition 2,
       :reason "Something bad happened",
       :source_uri "https://feed.example.com/12345/source",
       :disposition_name "Malicious",
       :priority 90,
       :id "judgement-01ae2d15-1364-4ceb-91f1-77b9b7f25187"
       :severity "High",
       :tlp "green",
       :confidence "High",
       :owner "Unknown"}
      {:valid_time
       {:start_time (two-months-ago)
        :end_time (one-month-ago)},
       :schema_version "0.4.3",
       :observable {:value "spam.eggs.net", :type "domain"},
       :reason_uri "https://feed.example.com/12345",
       :type "judgement",
       :created (two-months-ago)
       :source "Uber awesome feed thingy",
       :external_ids ["judgement-2"],
       :disposition 2,
       :reason "Something bad happened",
       :source_uri "https://feed.example.com/12345/source",
       :disposition_name "Malicious",
       :priority 90,
       :id "judgement-f15f59a7-8643-4f71-a7eb-93842e86972e"
       :severity "High",
       :tlp "green",
       :confidence "High",
       :owner "Unknown"}
      {:valid_time
       {:start_time (two-months-ago)
        :end_time (one-month-ago)},
       :schema_version "0.4.3",
       :observable {:value "spam.eggs.net", :type "domain"},
       :type "verdict",
       :created (two-months-ago)
       :disposition 2,
       :disposition_name "Malicious",
       :id "verdict-7e0fc41d-81d7-4a7f-ae96-57e01927588f",
       :judgement_id "judgement-01ae2d15-1364-4ceb-91f1-77b9b7f25187"}
      {:valid_time
       {:start_time (one-week-ago)
        :end_time (one-day-ago)},
       :schema_version "0.4.3",
       :observable {:value "spam.eggs.net", :type "domain"},
       :reason_uri "https://feed.example.com/12345",
       :type "judgement",
       :created (one-week-ago)
       :source "Uber awesome feed thingy",
       :external_ids ["judgement-3"],
       :disposition 2,
       :reason "Something bad happened",
       :source_uri "https://feed.example.com/12345/source",
       :disposition_name "Malicious",
       :priority 90,
       :id "judgement-6fd7b27b-251c-47a9-9ef7-69692229a637"
       :severity "High",
       :tlp "green",
       :confidence "High",
       :owner "Unknown"}
      {:valid_time
       {:start_time (one-week-ago)
        :end_time (one-day-ago)},
       :schema_version "0.4.3",
       :observable {:value "spam.eggs.net", :type "domain"},
       :type "verdict",
       :created (one-week-ago)
       :disposition 2,
       :disposition_name "Malicious",
       :id "verdict-bf3736c1-f8a4-42f5-87be-06440ede644d",
       :judgement_id "judgement-6fd7b27b-251c-47a9-9ef7-69692229a637"}])

    (let [{status :status}
          (get "ctia/domain/spam.eggs.net/verdict")]
      (is (= 404 status)))))
