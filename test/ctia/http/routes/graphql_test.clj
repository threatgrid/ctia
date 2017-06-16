(ns ctia.http.routes.graphql-test
  (:require [clj-momo.test-helpers
             [core :as mth]
             [http :refer [encode]]]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [fake-whoami-service :as whoami-helpers]
             [graphql :as gh]
             [search :refer [test-query-string-search]]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]
            [clojure.java.io :as io]
            [clojure.walk :as walk]))

(def judgement-1
  {"observable" {"value" "1.2.3.4"
                "type" "ip"}
   "external_ids" ["http://ex.tld/ctia/judgement/judgement-123"
                  "http://ex.tld/ctia/judgement/judgement-456"]
   "disposition" 2
   "disposition_name" "Malicious"
   "tlp" "green"
   "source" "test"
   "source_uri" "https://panacea.threatgrid.com/ips/1.2.3.4"
   "priority" 100
   "severity" "High"
   "confidence" "Low"
   "reason" "This is a bad IP address that talked to some evil servers"
   "reason_uri" "https://panacea.threatgrid.com/somefeed"
   "valid_time" {"start_time" "2016-02-11T00:40:48.000Z"
                 "end_time" "2025-03-11T00:40:48.000Z"}})

(def judgement-2
  {"observable" {"value" "1.2.3.4"
                "type" "ip"}
   "external_ids" ["http://ex.tld/ctia/judgement/judgement-789"]
   "disposition" 2
   "disposition_name" "Malicious"
   "tlp" "green"
   "source" "test"
   "source_uri" "https://panacea.threatgrid.com/ips/1.2.3.4"
   "priority" 100
   "severity" "High"
   "confidence" "High"
   "reason" "This is a bad IP address that talked to some evil servers"
   "reason_uri" "https://panacea.threatgrid.com/somefeed"
   "valid_time" {"start_time" "2016-02-11T00:40:48.000Z"
                 "end_time" "2025-03-11T00:40:48.000Z"}})

(def judgement-3
  {"observable" {"value" "8.8.8.8"
                 "type" "ip"}
   "external_ids" ["http://ex.tld/ctia/judgement/judgement-678"]
   "disposition" 2
   "disposition_name" "Malicious"
   "tlp" "green"
   "source" "test"
   "source_uri" "https://panacea.threatgrid.com/ips/8.8.8.8"
   "priority" 100
   "severity" "High"
   "confidence" "High"
   "reason" "This is a bad IP address that talked to some evil servers"
   "reason_uri" "https://panacea.threatgrid.com/somefeed"
   "valid_time" {"start_time" "2016-02-11T00:40:48.000Z"
                 "end_time" "2025-03-11T00:40:48.000Z"}})

(def indicator-1
  {"title" "Bad IP because someone said so"
   "tlp" "green"
   "producer" "someguy"
   "description" "We heard from this guy that this IP was not to be trusted"
   "indicator_type" ["C2" "IP Watchlist"]
   "valid_time" {"start_time" "2016-05-11T00:40:48.000Z"
                 "end_time" "2025-07-11T00:40:48.000Z"}})

(def indicator-2
  {"title" "Malware detected because someone said so"
   "tlp" "green"
   "producer" "someguy"
   "description" "We heard from this guy that a malware has been detected"
   "indicator_type" ["Malware Artifacts"]
   "valid_time" {"start_time" "2016-05-11T00:40:48.000Z"
                 "end_time" "2025-07-11T00:40:48.000Z"}})

(def indicator-3
  {"title" "Malware may modify files"
   "tlp" "green"
   "producer" "someguy"
   "description" "We heard from this guy that a malware may modify files"
   "indicator_type" ["Malware Artifacts"]
   "valid_time" {"start_time" "2016-05-11T00:40:48.000Z"
                 "end_time" "2025-07-11T00:40:48.000Z"}})

(def sighting-1
  {"description" "Hostnames that have resolved to 194.87.217.88"
   "confidence" "High"
   "count" 1
   "observables" [{"type" "ip", "value" "194.87.217.88"}]
   "observed_time" {"start_time" "2017-03-19T00:46:50.000Z"
                    "end_time" "2017-03-20T00:00:00.000Z"}
   "sensor" "process.sandbox"
   "source" "test"
   "tlp" "white"
   "source_uri" "http://www.virustotal.com/vtapi/v2/ip-address/report?ip=194.87.217.88"
   "relations" [{"origin" "VirusTotal Enrichment Module"
                 "relation" "Resolved_To",
                 "source" {"type" "domain", "value" "alegroup.info"},
                 "related" {"type" "ip", "value" "194.87.217.88"}}]})

(def sighting-2
  {"description" "URLs hosted at 194.87.217.87 have url scanner postive detections"
   "confidence" "High"
   "count" 1
   "observables" [{"type" "ip", "value" "194.87.217.87"}]
   "observed_time" {"start_time" "2017-03-22T00:46:00.000Z"
                    "end_time" "2017-03-24T10:43:51.000Z"}
   "sensor" "process.sandbox"
   "source" "test"
   "tlp" "white"
   "source_uri" "http://www.virustotal.com/vtapi/v2/ip-address/report?ip=194.87.217.87"
   "relations" [{"origin" "VirusTotal Enrichment Module"
                 "relation" "Hosted_By",
                 "source" {"type" "url", "value" "http://alegroup.info/"},
                 "related" {"type" "ip", "value" "194.87.217.87"}}]})

(defn feedback-1 [entity_id]
  {:feedback -1
   :reason "False positive"
   :entity_id entity_id})

(defn feedback-2 [entity_id]
  {:feedback 0
   :reason "Unknown"
   :entity_id entity_id})

(defn initialize-graphql-data []
  (let [i1 (gh/create-object "indicator" indicator-1)
        i2 (gh/create-object "indicator" indicator-2)
        i3 (gh/create-object "indicator" indicator-3)
        j1 (gh/create-object "judgement" judgement-1)
        j2 (gh/create-object "judgement" judgement-2)
        j3 (gh/create-object "judgement" judgement-3)
        s1 (gh/create-object "sighting" sighting-1)
        s2 (gh/create-object "sighting" sighting-2)]
    (gh/create-object "feedback" (feedback-1 (:id i1)))
    (gh/create-object "feedback" (feedback-2 (:id i1)))
    (gh/create-object "feedback" (feedback-1 (:id j1)))
    (gh/create-object "feedback" (feedback-2 (:id j1)))
    (gh/create-object "feedback" (feedback-1 (:id s1)))
    (gh/create-object "feedback" (feedback-2 (:id s1)))
    (gh/create-object "relationship"
                   {:relationship_type "element-of"
                    :target_ref (:id i1)
                    :source_ref (:id j1)})
    (gh/create-object "relationship"
                   {:relationship_type "element-of"
                    :target_ref (:id i2)
                    :source_ref (:id j1)})
    (gh/create-object "relationship"
                   {:relationship_type "element-of"
                    :target_ref (:id i1)
                    :source_ref (:id j2)})
    (gh/create-object "relationship"
                   {:relationship_type "element-of"
                    :target_ref (:id i1)
                    :source_ref (:id j3)})
    (gh/create-object "relationship"
                   {:relationship_type "indicates"
                    :target_ref (:id i1)
                    :source_ref (:id s1)})
    (gh/create-object "relationship"
                      {:relationship_type "indicates"
                       :target_ref (:id i2)
                       :source_ref (:id s1)})
    (gh/create-object "relationship"
                   {:relationship_type "variant-of"
                    :target_ref (:id i2)
                    :source_ref (:id i1)})
    (gh/create-object "relationship"
                   {:relationship_type "variant-of"
                    :target_ref (:id i3)
                    :source_ref (:id i1)})
    {:indicator-1 i1
     :indicator-2 i2
     :indicator-3 i3
     :judgement-1 j1
     :judgement-2 j2
     :judgement-3 j3
     :sighting-1 s1
     :sighting-2 s2}))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn nodes->edges
  [nodes]
  (mapv (fn [node]
          {:node node})
        nodes))

(defn connection-test
  "Test a connection with more than one edge"
  [operation-name
   graphql-query
   variables
   connection-path
   expected-nodes]
  ;; page 1
  (let [{:keys [data errors status]}
        (gh/query graphql-query
                  (into variables
                        {:first 1})
                  operation-name)]
    (is (= 200 status))
    (is (empty? errors) "No errors")
    (let [{nodes-p1 :nodes
           edges-p1 :edges
           page-info-p1 :pageInfo
           total-count-p1 :totalCount}
          (get-in data connection-path)]
      (is (= (count expected-nodes)
             total-count-p1))
      (is (= (count nodes-p1) 1)
          "The first page contains 1 node")
      (is (= (count edges-p1) 1)
          "The first page contains 1 edge")
      ;; page 2
      (let [{:keys [data errors status]}
            (gh/query graphql-query
                   (into variables
                         {:first 50
                          :after (:endCursor page-info-p1)})
                   operation-name)]
        (is (= 200 status))
        (is (empty? errors) "No errors")
        (let [{nodes-p2 :nodes
               edges-p2 :edges
               page-info-p2 :pageInfo
               total-count-p2 :totalCount}
              (get-in data connection-path)]
          (is (= (count expected-nodes)
                 total-count-p2))
          (is (= (count nodes-p2) (- (count expected-nodes)
                                     (count nodes-p1)))
              "The second page contains all remaining nodes")
          (is (= (count edges-p2) (- (count expected-nodes)
                                     (count edges-p1)))
              "The second page contains all remaining edges")
          (is (= (set expected-nodes)
                 (set (concat nodes-p1 nodes-p2)))
              "All nodes have been retrieved")
          (is (= (set (nodes->edges expected-nodes))
                 (set (concat edges-p1 edges-p2)))
              "All edges have been retrieved"))))))

(deftest-for-each-store test-graphql-route
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")
  (let [datamap (initialize-graphql-data)
        indicator-1-id (get-in datamap [:indicator-1 :id])
        indicator-2-id (get-in datamap [:indicator-2 :id])
        indicator-3-id (get-in datamap [:indicator-3 :id])
        judgement-1-id (get-in datamap [:judgement-1 :id])
        judgement-2-id (get-in datamap [:judgement-2 :id])
        judgement-3-id (get-in datamap [:judgement-3 :id])
        sighting-1-id (get-in datamap [:sighting-1 :id])
        graphql-queries (slurp "test/data/queries.graphql")]

    (testing "POST /ctia/graphql"

      (testing "Query syntax error"
        (let [{:keys [data errors status]} (gh/query "dummy" {} "")]
          (is (= 400 status))
          (is (= errors
                 ["InvalidSyntaxError{sourceLocations=[SourceLocation{line=1, column=0}]}"]))))

      (testing "Query validation error"
        (let [{:keys [data errors status]}
              (gh/query "query TestQuery { nonexistent }"
                     {}
                     "TestQuery")]
          (is (= 400 status))
          (is (= errors
                 ["ValidationError{validationErrorType=FieldUndefined, sourceLocations=[SourceLocation{line=1, column=19}], description='Field nonexistent is undefined'}"]))))

      (testing "observable query"
        (let [{:keys [data errors status]}
              (gh/query graphql-queries
                     {:type "ip"
                      :value "1.2.3.4"}
                     "ObservableQueryTest")]
          (is (= 200 status))
          (is (empty? errors) "No errors")

          (testing "the observable"
            (is (= (dissoc (:observable data) :verdict :judgements)
                   {:type "ip"
                    :value "1.2.3.4"})))

          (testing "the verdict"
            (let [verdict (get-in data [:observable :verdict])]
              (is (= {:type "verdict",
                      :disposition 2,
                      :disposition_name "Malicious",
                      :observable {:type "ip", :value "1.2.3.4"},
                      :judgement_id judgement-1-id}
                     (dissoc verdict :judgement)))

              (testing "the judgement"
                (is (= (:judgement-1 datamap)
                       (:judgement verdict))))))

          (testing "judgements connection"
              (connection-test "ObservableQueryTest"
                               graphql-queries
                               {:type "ip"
                                :value "1.2.3.4"}
                               [:observable :judgements]
                               [(:judgement-1 datamap)
                                (:judgement-2 datamap)]))))

      (testing "judgement query"
        (let [{:keys [data errors status]}
              (gh/query graphql-queries
                     {:id judgement-1-id}
                     "JudgementQueryTest")]
          (is (= 200 status))
          (is (empty? errors) "No errors")

          (testing "the judgement"
            (is (= (:judgement-1 datamap)
                   (dissoc (:judgement data)
                           :relationships))))

          (testing "relationships connection"
            (connection-test "JudgementQueryTest"
                             graphql-queries
                             {:id judgement-1-id}
                             [:judgement :relationships]
                             [{:relationship_type "element-of"
                               :target_ref indicator-1-id
                               :source_ref judgement-1-id
                               :source (:judgement-1 datamap)
                               :target (:indicator-1 datamap)}
                              {:relationship_type "element-of"
                               :target_ref indicator-2-id
                               :source_ref judgement-1-id
                               :source (:judgement-1 datamap)
                               :target (:indicator-2 datamap)}]))

          (testing "feedbacks connection"
            (connection-test "JudgementFeedbacksQueryTest"
                             graphql-queries
                             {:id judgement-1-id}
                             [:judgement :feedbacks]
                             [(feedback-1 judgement-1-id)
                              (feedback-2 judgement-1-id)]))))

      (testing "judgements query"
        (testing "judgements connection"
          (connection-test "JudgementsQueryTest"
                           graphql-queries
                           {:query "*"}
                           [:judgements]
                           [(:judgement-1 datamap)
                            (:judgement-2 datamap)
                            (:judgement-3 datamap)]))

        (testing "query argument"
          (let [{:keys [data errors status]}
                (gh/query graphql-queries
                       {:query (format "external_ids:\"%s\""
                                       "http://ex.tld/ctia/judgement/judgement-123")}
                       "JudgementsQueryTest")]
            (is (= 200 status))
            (is (empty? errors) "No errors")
            (is (= [(:judgement-1 datamap)]
                   (get-in data [:judgements :nodes]))
                "The judgement matches the search query"))))

      (testing "indicator query"
        (let [{:keys [data errors status]}
              (gh/query graphql-queries
                     {:id indicator-1-id}
                     "IndicatorQueryTest")]
          (is (= 200 status))
          (is (empty? errors) "No errors")

          (testing "the indicator"
            (is (= (:indicator-1 datamap)
                   (dissoc (:indicator data)
                           :relationships))))

          (testing "relationships connection"
            (connection-test "IndicatorQueryTest"
                             graphql-queries
                             {:id indicator-1-id}
                             [:indicator :relationships]
                             [{:relationship_type "variant-of"
                               :target_ref indicator-2-id
                               :source_ref indicator-1-id
                               :source (:indicator-1 datamap)
                               :target (:indicator-2 datamap)}
                              {:relationship_type "variant-of"
                               :target_ref indicator-3-id
                               :source_ref indicator-1-id
                               :source (:indicator-1 datamap)
                               :target (:indicator-3 datamap)}]))

          (testing "feedbacks connection"
            (connection-test "IndicatorFeedbacksQueryTest"
                             graphql-queries
                             {:id indicator-1-id}
                             [:indicator :feedbacks]
                             [(feedback-1 indicator-1-id)
                              (feedback-2 indicator-1-id)]))))

      (testing "indicators query"

        (testing "indicators connection"
          (connection-test "IndicatorsQueryTest"
                           graphql-queries
                           {"query" "*"}
                           [:indicators]
                           [(:indicator-1 datamap)
                            (:indicator-2 datamap)
                            (:indicator-3 datamap)]))

        (testing "query argument"
          (let [{:keys [data errors status]}
                (gh/query graphql-queries
                       {:query "indicator_type:\"C2\""}
                       "IndicatorsQueryTest")]
            (is (= 200 status))
            (is (empty? errors) "No errors")
            (is (= 1 (get-in data [:indicators :totalCount]))
                "Only one indicator matches to the query")
            (is (= [(:indicator-1 datamap)]
                   (get-in data [:indicators :nodes]))
                "The indicator matches the search query"))))

      (testing "sighting query"
        (let [{:keys [data errors status]}
              (gh/query graphql-queries
                     {:id (get-in datamap [:sighting-1 :id])}
                     "SightingQueryTest")]
          (is (= 200 status))
          (is (empty? errors) "No errors")

          (testing "the sighting"
            (is (= (:sighting-1 datamap)
                   (-> (:sighting data)
                       (dissoc :relationships)))))

          (testing "relationships connection"
            (connection-test "SightingQueryTest"
                             graphql-queries
                             {:id sighting-1-id}
                             [:sighting :relationships]
                             [{:relationship_type "indicates"
                               :target_ref indicator-1-id
                               :source_ref sighting-1-id
                               :source (:sighting-1 datamap)
                               :target (:indicator-1 datamap)}
                              {:relationship_type "indicates"
                               :target_ref indicator-2-id
                               :source_ref sighting-1-id
                               :source (:sighting-1 datamap)
                               :target (:indicator-2 datamap)}]))

          (testing "feedbacks connection"
            (connection-test "SightingFeedbacksQueryTest"
                             graphql-queries
                             {:id sighting-1-id}
                             [:sighting :feedbacks]
                             [(feedback-1 sighting-1-id)
                              (feedback-2 sighting-1-id)]))))

      (testing "sightings query"

        (testing "sightings connection"
          (connection-test "SightingsQueryTest"
                           graphql-queries
                           {"query" "*"}
                           [:sightings]
                           [(:sighting-1 datamap)
                            (:sighting-2 datamap)]))

        (testing "query argument"
          (let [{:keys [data errors status]}
                (gh/query graphql-queries
                       {:query (format "description:\"%s\""
                                       (get sighting-1 "description"))}
                       "SightingsQueryTest")]
            (is (= 200 status))
            (is (empty? errors) "No errors")
            (is (= 1 (get-in data [:sightings :totalCount]))
                "Only one sighting matches to the query")
            (is (= (:sighting-1 datamap)
                   (first (get-in data [:sightings :nodes])))
                "The sighting matches the search query")))))))
