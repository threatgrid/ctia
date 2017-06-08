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

(defn create-object [type obj]
  (let [{status :status
         body :parsed-body}
        (helpers/post (str "ctia/" type)
              :body obj
              :headers {"api_key" "45c1f5e3f05d0"})]
    (if (not (= status 201))
      (throw (Exception. (str "Failed to create " (pr-str type) " obj: " (pr-str obj) "Status: " status "Response:" body))))
    body))

(defn initialize-graphql-data []
  (let [i1 (create-object "indicator" indicator-1)
        i2 (create-object "indicator" indicator-2)
        i3 (create-object "indicator" indicator-3)
        j1 (create-object "judgement" judgement-1)
        j2 (create-object "judgement" judgement-2)
        j3 (create-object "judgement" judgement-3)
        s1 (create-object "sighting" sighting-1)
        s2 (create-object "sighting" sighting-2)]
    (create-object "relationship"
                   {:relationship_type "element-of"
                    :target_ref (:id i1)
                    :source_ref (:id j1)})
    (create-object "relationship"
                   {:relationship_type "element-of"
                    :target_ref (:id i2)
                    :source_ref (:id j1)})
    (create-object "relationship"
                   {:relationship_type "element-of"
                    :target_ref (:id i1)
                    :source_ref (:id j2)})
    (create-object "relationship"
                   {:relationship_type "element-of"
                    :target_ref (:id i1)
                    :source_ref (:id j3)})
    (create-object "relationship"
                   {:relationship_type "indicates"
                    :target_ref (:id i2)
                    :source_ref (:id s1)})
    (create-object "relationship"
                   {:relationship_type "variant-of"
                    :target_ref (:id i2)
                    :source_ref (:id i1)})
    (create-object "relationship"
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

;;ctia.test-helpers.core/fixture-properties:events-enabled

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn nodes->edges
  [nodes]
  (mapv (fn [node]
          {"node" node})
        nodes))

(defn connection-test
  "Test a connection with more than one edge"
  [operation-name
   query
   variables
   connection-path
   expected-nodes]
  (let [{status :status
         body :parsed-body}
        (helpers/post "ctia/graphql"
                      :body {:query query
                             :variables (into variables
                                              {:first 1})
                             :operationName operation-name}
                      :headers {"api_key" "45c1f5e3f05d0"})]
    (is (= 200 status))
    (is (empty? (:errors body))
        "No errors")
    (let [{nodes-p1 "nodes"
           edges-p1 "edges"
           page-info-p1 "pageInfo"
           total-count-p1 "totalCount"}
          (get-in body (concat [:data] connection-path))]
      (is (= (count expected-nodes)
             total-count-p1))
      (is (= (count nodes-p1) 1)
          "The first page contains 1 node")
      (is (= (count edges-p1) 1)
          "The first page contains 1 edge")
      (let [{status :status
             body :parsed-body}
            (helpers/post "ctia/graphql"
                          :body {:query query
                                 :variables (into variables
                                                  {:first 50
                                                   :after (get page-info-p1 "endCursor")})
                                 :operationName operation-name}
                          :headers {"api_key" "45c1f5e3f05d0"})]
        (is (= 200 status))
        (is (empty? (:errors body))
            "No errors")
        (let [{nodes-p2 "nodes"
               edges-p2 "edges"
               page-info-p2 "pageInfo"
               total-count-p2 "totalCount"}
              (get-in body (concat [:data] connection-path))]
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
        graphql-queries (slurp "test/data/queries.graphql")]

    (testing "POST /ctia/graphql"

      (testing "Query syntax error"
        (let [{status :status
               body :parsed-body}
              (helpers/post "ctia/graphql"
                    :body {:query "dummy"}
                    :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 400 status))
          (is (deep= body
                     {:errors ["InvalidSyntaxError{sourceLocations=[SourceLocation{line=1, column=0}]}"]}))))

      (testing "Query validation error"
        (let [{status :status
               body :parsed-body}
              (helpers/post "ctia/graphql"
                    :body {:query "query TestQuery { nonexistent }"}
                    :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 400 status))
          (is (deep= body
                     {:errors ["ValidationError{validationErrorType=FieldUndefined, sourceLocations=[SourceLocation{line=1, column=19}], description='Field nonexistent is undefined'}"]}))))

      (testing "observable query"
        (let [{status :status
               body :parsed-body}
              (helpers/post "ctia/graphql"
                            :body {:query graphql-queries
                                   :variables {:type "ip"
                                               :value "1.2.3.4"}
                                   :operationName "ObservableQueryTest"}
                            :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (empty? (:errors body))
              "No errors")

          (testing "the observable"
            (is (= (dissoc (get-in body [:data "observable"]) "verdict" "judgements")
                   {"type" "ip"
                    "value" "1.2.3.4"})))

          (testing "the verdict"
            (let [verdict (get-in body [:data "observable" "verdict"])]
              (is (= {"type" "verdict",
                      "disposition" 2,
                      "disposition_name" "Malicious",
                      "observable" {"type" "ip", "value" "1.2.3.4"},
                      "judgement_id" judgement-1-id}
                     (dissoc verdict "judgement")))

              (testing "the judgement"
                (let [judgement (get verdict "judgement")]
                  (is (= (assoc judgement-1
                                "id" judgement-1-id
                                "type" "judgement")
                         judgement))))))

          (testing "judgements connection"
              (connection-test "ObservableQueryTest"
                               graphql-queries
                               {:type "ip"
                                :value "1.2.3.4"}
                               ["observable" "judgements"]
                               [(assoc judgement-1
                                       "id" judgement-1-id
                                       "type" "judgement")
                                (assoc judgement-2
                                       "id" judgement-2-id
                                       "type" "judgement")]))))

      (testing "judgement query"
        (let [{status :status
               body :parsed-body}
              (helpers/post "ctia/graphql"
                            :body {:query graphql-queries
                                   :variables {:id judgement-1-id}
                                   :operationName "JudgementQueryTest"}
                            :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (empty? (:errors body))
              "No errors")

          (testing "the judgement"
            (let [judgement (-> (get-in body [:data "judgement"])
                                (dissoc "relationships"))]
              (is (= (assoc judgement-1
                            "id" judgement-1-id
                            "type" "judgement")
                     judgement))))

          (testing "relationships connection"
            (connection-test "JudgementQueryTest"
                             graphql-queries
                             {:id judgement-1-id}
                             ["judgement" "relationships"]
                             [{"relationship_type" "element-of"
                               "target_ref" indicator-1-id
                               "source_ref" judgement-1-id
                               "source" (assoc judgement-1
                                               "id" judgement-1-id
                                               "type" "judgement")
                               "target" (assoc indicator-1
                                               "id" indicator-1-id
                                               "type" "indicator")}
                              {"relationship_type" "element-of"
                               "target_ref" indicator-2-id
                               "source_ref" judgement-1-id
                               "source" (assoc judgement-1
                                               "id" judgement-1-id
                                               "type" "judgement")
                               "target" (assoc indicator-2
                                               "id" indicator-2-id
                                               "type" "indicator")}]))))

      (testing "judgements query"
        (testing "judgements connection"
          (connection-test "JudgementsQueryTest"
                           graphql-queries
                           {"query" "*"}
                           ["judgements"]
                           [(assoc judgement-1
                                   "id" judgement-1-id
                                   "type" "judgement")
                            (assoc judgement-2
                                   "id" judgement-2-id
                                   "type" "judgement")
                            (assoc judgement-3
                                   "id" judgement-3-id
                                   "type" "judgement")]))

        (testing "query argument"
          (let [{status :status
                 body :parsed-body}
                (helpers/post
                 "ctia/graphql"
                 :body {:query graphql-queries
                        :variables
                        {:query (format "external_ids:\"%s\""
                                        "http://ex.tld/ctia/judgement/judgement-123")}
                        :operationName "JudgementsQueryTest"}
                 :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 200 status))
            (is (empty? (:errors body))
                "No errors")
            (is (= [(assoc judgement-1
                           "id" judgement-1-id
                           "type" "judgement")]
                   (get-in body [:data "judgements" "nodes"]))
                "The judgement matches the search query"))))

      (testing "indicator query"
        (let [{status :status
               body :parsed-body}
              (helpers/post "ctia/graphql"
                            :body {:query graphql-queries
                                   :variables {:id indicator-1-id}
                                   :operationName "IndicatorQueryTest"}
                            :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (empty? (:errors body))
              "No errors")

          (testing "the indicator"
            (let [indicator (-> (get-in body [:data "indicator"])
                                (dissoc "relationships"))]
              (is (= (assoc indicator-1
                            "id" (get-in datamap [:indicator-1 :id])
                            "type" "indicator")
                     indicator))))

          (testing "relationships connection"
            (connection-test "IndicatorQueryTest"
                             graphql-queries
                             {:id indicator-1-id}
                             ["indicator" "relationships"]
                             [{"relationship_type" "variant-of"
                               "target_ref" indicator-2-id
                               "source_ref" indicator-1-id
                               "source" (assoc indicator-1
                                               "id" indicator-1-id
                                               "type" "indicator")
                               "target" (assoc indicator-2
                                               "id" indicator-2-id
                                               "type" "indicator")}
                              {"relationship_type" "variant-of"
                               "target_ref" indicator-3-id
                               "source_ref" indicator-1-id
                               "source" (assoc indicator-1
                                               "id" indicator-1-id
                                               "type" "indicator")
                               "target" (assoc indicator-3
                                               "id" indicator-3-id
                                               "type" "indicator")}]))))

      (testing "indicators query"

        (testing "indicators connection"
          (connection-test "IndicatorsQueryTest"
                           graphql-queries
                           {"query" "*"}
                           ["indicators"]
                           [(assoc indicator-1
                                   "id" (get-in datamap [:indicator-1 :id])
                                   "type" "indicator")
                            (assoc indicator-2
                                   "id" (get-in datamap [:indicator-2 :id])
                                   "type" "indicator")
                            (assoc indicator-3
                                   "id" (get-in datamap [:indicator-3 :id])
                                   "type" "indicator")]))

        (testing "query argument"
          (let [{status :status
                 body :parsed-body}
                (helpers/post "ctia/graphql"
                              :body {:query graphql-queries
                                     :variables {:query "indicator_type:\"C2\""}
                                     :operationName "IndicatorsQueryTest"}
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 200 status))
            (is (empty? (:errors body))
                "No errors")
            (is (= 1 (get-in body [:data "indicators" "totalCount"]))
                "Only one indicator matches to the query")
            (is (= (assoc indicator-1
                          "id" (get-in datamap [:indicator-1 :id])
                          "type" "indicator")
                   (first (get-in body [:data "indicators" "nodes"])))
                "The indicator matches the search query"))))

      (testing "sighting query"
        (let [{status :status
               body :parsed-body}
              (helpers/post "ctia/graphql"
                            :body {:query graphql-queries
                                   :variables {:id (get-in datamap [:sighting-1 :id])}
                                   :operationName "SightingQueryTest"}
                            :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (empty? (:errors body))
              "No errors")

          (testing "the sighting"
            (let [sighting (-> (get-in body [:data "sighting"])
                               (dissoc "relationships"))]
              (is (= (assoc sighting-1
                            "id" (get-in datamap [:sighting-1 :id])
                            "type" "sighting")
                     sighting))))))

      (testing "sightings query"

        (testing "sightings connection"
          (connection-test "SightingsQueryTest"
                           graphql-queries
                           {"query" "*"}
                           ["sightings"]
                           [(assoc sighting-1
                                   "id" (get-in datamap [:sighting-1 :id])
                                   "type" "sighting")
                            (assoc sighting-2
                                   "id" (get-in datamap [:sighting-2 :id])
                                   "type" "sighting")]))

        (testing "query argument"
          (let [{status :status
                 body :parsed-body}
                (helpers/post "ctia/graphql"
                              :body {:query graphql-queries
                                     :variables {:query (format "description:\"%s\""
                                                                (get sighting-1 "description"))}
                                     :operationName "SightingsQueryTest"}
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 200 status))
            (is (empty? (:errors body))
                "No errors")
            (is (= 1 (get-in body [:data "sightings" "totalCount"]))
                "Only one sighting matches to the query")
            (is (= (assoc sighting-1
                          "id" (get-in datamap [:sighting-1 :id])
                          "type" "sighting")
                   (first (get-in body [:data "sightings" "nodes"])))
                "The sighting matches the search query")))))))
