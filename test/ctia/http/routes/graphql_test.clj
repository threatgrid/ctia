(ns ctia.http.routes.graphql-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.schemas.sorting :as fields]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [fake-whoami-service :as whoami-helpers]
             [graphql :as gh]
             [store :refer [test-for-each-store]]]
            [ctim.examples.casebooks :refer [new-casebook-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

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
   "reason" "This is a very bad IP address that talked to some evil servers"
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
   "priority" 90
   "severity" "Medium"
   "confidence" "Medium"
   "reason" "This is a bad IP address that talked to some evil servers"
   "reason_uri" "https://panacea.threatgrid.com/somefeed"
   "valid_time" {"start_time" "2016-02-11T00:40:48.000Z"
                 "end_time" "2025-03-11T00:40:48.000Z"}})

(def judgement-3
  {"observable" {"value" "8.8.8.8"
                 "type" "ip"}
   "external_ids" ["http://ex.tld/ctia/judgement/judgement-678"]
   "disposition" 1
   "disposition_name" "Clean"
   "tlp" "green"
   "source" "test"
   "source_uri" "https://panacea.threatgrid.com/ips/8.8.8.8"
   "priority" 80
   "severity" "None"
   "confidence" "High"
   "reason" "This is a clean IP address"
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

(def investigation-1
  {"source" "foo"
   "title" "foo"})

(def investigation-2
  {"source" "bar"
   "title" "foo"})

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
(def casebook-1
  (-> new-casebook-maximal
      (assoc :title "foo")
      (dissoc :id)))

(def casebook-2
  (-> new-casebook-maximal
      (assoc :title "bar")
      (dissoc :id)))

(defn feedback-1 [entity_id]
  {:feedback -1
   :reason "False positive"
   :entity_id entity_id})

(defn feedback-2 [entity_id]
  {:feedback 0
   :reason "Unknown"
   :entity_id entity_id})


(defn initialize-graphql-data []
  (let [i1  (gh/create-object "indicator" indicator-1)
        i2  (gh/create-object "indicator" indicator-2)
        i3  (gh/create-object "indicator" indicator-3)
        in1 (gh/create-object "investigation" investigation-1)
        in2 (gh/create-object "investigation" investigation-2)
        j1  (gh/create-object "judgement" judgement-1)
        j2  (gh/create-object "judgement" judgement-2)
        j3  (gh/create-object "judgement" judgement-3)
        sc1 (gh/create-object "casebook" casebook-1)
        sc2 (gh/create-object "casebook" casebook-2)
        s1  (gh/create-object "sighting" sighting-1)
        s2  (gh/create-object "sighting" sighting-2)]
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
     :investigation-1 in1
     :investigation-2 in2
     :judgement-1 j1
     :judgement-2 j2
     :judgement-3 j3
     :casebook-1 sc1
     :casebook-2 sc2
     :sighting-1 s1
     :sighting-2 s2}))

(deftest test-graphql-route
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (helpers/set-capabilities! "baruser" ["bargroup"] "user" #{})
     (whoami-helpers/set-whoami-response "2222222222222" "baruser" "bargroup" "user")

     (let [datamap (initialize-graphql-data)
           indicator-1-id (get-in datamap [:indicator-1 :id])
           indicator-2-id (get-in datamap [:indicator-2 :id])
           indicator-3-id (get-in datamap [:indicator-3 :id])
           investigation-1-id (get-in datamap [:investigation-1 :id])
           investigation-2-id (get-in datamap [:investigation-2 :id])
           casebook-1-id (get-in datamap [:casebook-1 :id])
           casebook-2-id (get-in datamap [:casebook-2 :id])
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
                    ["InvalidSyntaxError{ message=Invalid Syntax ,locations=[SourceLocation{line=1, column=0}]}"]))))

         (testing "Query validation error"
           (let [{:keys [data errors status]}
                 (gh/query "query TestQuery { nonexistent }"
                           {}
                           "TestQuery")]
             (is (= 400 status))
             (is (= errors
                    ["ValidationError{validationErrorType=FieldUndefined, message=Validation error of type FieldUndefined: Field 'nonexistent' in type 'Root' is undefined, locations=[SourceLocation{line=1, column=19}], description='Field 'nonexistent' in type 'Root' is undefined'}"]))))
         (testing "unauthorized access without capabilities"
           (let [{:keys [status]}
                 (helpers/post "ctia/graphql"
                               :body {:query ""
                                      :variables {}
                                      :operationName ""}
                               :headers {"Authorization" "2222222222222"})]
             (is (= 401 status))))
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
               (gh/connection-test "ObservableQueryTest"
                                   graphql-queries
                                   {:type "ip"
                                    :value "1.2.3.4"}
                                   [:observable :judgements]
                                   [(:judgement-1 datamap)
                                    (:judgement-2 datamap)])

               (testing "sorting"
                 (gh/connection-sort-test
                  "ObservableQueryTest"
                  graphql-queries
                  {:type "ip"
                   :value "1.2.3.4"}
                  [:observable :judgements]
                  ctia.entity.judgement.schemas/judgement-fields)))))

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
               (gh/connection-test "JudgementQueryTest"
                                   graphql-queries
                                   {:id judgement-1-id}
                                   [:judgement :relationships]
                                   [{:relationship_type "element-of"
                                     :target_ref indicator-1-id
                                     :source_ref judgement-1-id
                                     :source_entity (:judgement-1 datamap)
                                     :target_entity (:indicator-1 datamap)}
                                    {:relationship_type "element-of"
                                     :target_ref indicator-2-id
                                     :source_ref judgement-1-id
                                     :source_entity (:judgement-1 datamap)
                                     :target_entity (:indicator-2 datamap)}])

               (testing "sorting"
                 (gh/connection-sort-test
                  "JudgementQueryTest"
                  graphql-queries
                  {:id judgement-1-id}
                  [:judgement :relationships]
                  ctia.entity.relationship/relationship-fields)))

             (testing "feedbacks connection"
               (gh/connection-test "JudgementFeedbacksQueryTest"
                                   graphql-queries
                                   {:id judgement-1-id}
                                   [:judgement :feedbacks]
                                   [(feedback-1 judgement-1-id)
                                    (feedback-2 judgement-1-id)])

               (testing "sorting"
                 (gh/connection-sort-test
                  "JudgementFeedbacksQueryTest"
                  graphql-queries
                  {:id judgement-1-id}
                  [:judgement :feedbacks]
                  ctia.entity.feedback.schemas/feedback-fields)))))

         (testing "judgements query"
           (testing "judgements connection"
             (gh/connection-test "JudgementsQueryTest"
                                 graphql-queries
                                 {:query "*"}
                                 [:judgements]
                                 [(:judgement-1 datamap)
                                  (:judgement-2 datamap)
                                  (:judgement-3 datamap)])

             (testing "sorting"
               (gh/connection-sort-test
                "JudgementsQueryTest"
                graphql-queries
                {:query "*"}
                [:judgements]
                ctia.entity.judgement.schemas/judgement-fields)))

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
               (gh/connection-test "IndicatorQueryTest"
                                   graphql-queries
                                   {:id indicator-1-id}
                                   [:indicator :relationships]
                                   [{:relationship_type "variant-of"
                                     :target_ref indicator-2-id
                                     :source_ref indicator-1-id
                                     :source_entity (:indicator-1 datamap)
                                     :target_entity (:indicator-2 datamap)}
                                    {:relationship_type "variant-of"
                                     :target_ref indicator-3-id
                                     :source_ref indicator-1-id
                                     :source_entity (:indicator-1 datamap)
                                     :target_entity (:indicator-3 datamap)}])

               (testing "sorting"
                 (gh/connection-sort-test
                  "IndicatorQueryTest"
                  graphql-queries
                  {:id indicator-1-id}
                  [:indicator :relationships]
                  ctia.entity.relationship/relationship-fields)))

             (testing "feedbacks connection"
               (gh/connection-test "IndicatorFeedbacksQueryTest"
                                   graphql-queries
                                   {:id indicator-1-id}
                                   [:indicator :feedbacks]
                                   [(feedback-1 indicator-1-id)
                                    (feedback-2 indicator-1-id)])

               (testing "sorting"
                 (gh/connection-sort-test
                  "IndicatorFeedbacksQueryTest"
                  graphql-queries
                  {:id indicator-1-id}
                  [:indicator :feedbacks]
                  ctia.entity.feedback.schemas/feedback-fields)))))

         (testing "indicators query"
           (testing "indicators connection"
             (gh/connection-test "IndicatorsQueryTest"
                                 graphql-queries
                                 {"query" "*"}
                                 [:indicators]
                                 [(:indicator-1 datamap)
                                  (:indicator-2 datamap)
                                  (:indicator-3 datamap)])

             (testing "sorting"
               (gh/connection-sort-test
                "IndicatorsQueryTest"
                graphql-queries
                {:query "*"}
                [:indicators]
                ctia.entity.indicator/indicator-fields)))

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

         (testing "investigation query"
           (let [{:keys [data errors status]}
                 (gh/query graphql-queries
                           {:id investigation-1-id}
                           "InvestigationQueryTest")]

             (is (= 200 status))
             (is (empty? errors) "No errors")

             (testing "the investigation"
               (is (= (:investigation-1 datamap)
                      (:investigation data))))))

         (testing "investigations query"
           (testing "investigations connection"
             (gh/connection-test "InvestigationsQueryTest"
                                 graphql-queries
                                 {"query" "*"}
                                 [:investigations]
                                 [(:investigation-1 datamap)
                                  (:investigation-2 datamap)])

             (testing "sorting"
               (gh/connection-sort-test
                "InvestigationsQueryTest"
                graphql-queries
                {:query "*"}
                [:investigations]
                ctia.entity.investigation/investigation-fields)))



           (testing "query argument"
             (let [{:keys [data errors status]}
                   (gh/query graphql-queries
                             {:query "source:\"foo\""}
                             "InvestigationsQueryTest")]
               (is (= 200 status))
               (is (empty? errors) "No errors")
               (is (= 1 (get-in data [:investigations :totalCount]))
                   "Only one investigation matches to the query")
               (is (= [(:investigation-1 datamap)]
                      (get-in data [:investigations :nodes]))
                   "The investigation matches the search query"))))

                                        ;------------------

         (testing "casebook query"
           (let [{:keys [data errors status]}
                 (gh/query graphql-queries
                           {:id casebook-1-id}
                           "CasebookQueryTest")]

             (is (= 200 status))
             (is (empty? errors) "No errors")

             (testing "the casebook"
               (is (= (dissoc (:casebook-1 datamap) :bundle :texts)
                      (:casebook data))))))

         (testing "casebooks query"
           (testing "casebooks connection"
             (gh/connection-test "CasebooksQueryTest"
                                 graphql-queries
                                 {"query" "*"}
                                 [:casebooks]
                                 [(dissoc (:casebook-1 datamap) :bundle :texts)
                                  (dissoc (:casebook-2 datamap) :bundle :texts)])

             (testing "sorting"
               (gh/connection-sort-test
                "CasebooksQueryTest"
                graphql-queries
                {:query "*"}
                [:casebooks]
                ctia.entity.casebook/casebook-fields)))

           (testing "query argument"
             (let [{:keys [data errors status]}
                   (gh/query graphql-queries
                             {:query "title:\"foo\""}
                             "CasebooksQueryTest")]
               (is (= 200 status))
               (is (empty? errors) "No errors")
               (is (= 1 (get-in data [:casebooks :totalCount]))
                   "Only one casebook matches to the query")
               (is (= [(dissoc (:casebook-1 datamap) :bundle :texts)]
                      (get-in data [:casebooks :nodes]))
                   "The casebook matches the search query"))))

                                        ;---------------------------
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
               (gh/connection-test "SightingQueryTest"
                                   graphql-queries
                                   {:id sighting-1-id}
                                   [:sighting :relationships]
                                   [{:relationship_type "indicates"
                                     :target_ref indicator-1-id
                                     :source_ref sighting-1-id
                                     :source_entity (:sighting-1 datamap)
                                     :target_entity (:indicator-1 datamap)}
                                    {:relationship_type "indicates"
                                     :target_ref indicator-2-id
                                     :source_ref sighting-1-id
                                     :source_entity (:sighting-1 datamap)
                                     :target_entity (:indicator-2 datamap)}])

               (testing "sorting"
                 (gh/connection-sort-test
                  "SightingQueryTest"
                  graphql-queries
                  {:id sighting-1-id}
                  [:sighting :relationships]
                  ctia.entity.relationship/relationship-fields)))

             (testing "feedbacks connection"
               (gh/connection-test "SightingFeedbacksQueryTest"
                                   graphql-queries
                                   {:id sighting-1-id}
                                   [:sighting :feedbacks]
                                   [(feedback-1 sighting-1-id)
                                    (feedback-2 sighting-1-id)])

               (testing "sorting"
                 (gh/connection-sort-test
                  "SightingFeedbacksQueryTest"
                  graphql-queries
                  {:id sighting-1-id}
                  [:sighting :feedbacks]
                  ctia.entity.feedback.schemas/feedback-fields)))))

         (testing "sightings query"

           (testing "sightings connection"
             (gh/connection-test "SightingsQueryTest"
                                 graphql-queries
                                 {"query" "*"}
                                 [:sightings]
                                 [(:sighting-1 datamap)
                                  (:sighting-2 datamap)])

             (testing "sorting"
               (gh/connection-sort-test
                "SightingsQueryTest"
                graphql-queries
                {:query "*"}
                [:sightings]
                ctia.entity.sighting.schemas/sighting-fields)))

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
                   "The sighting matches the search query"))))
         (testing "Relationships without selection of target_ref"
           (let [{:keys [data errors status]}
                 (gh/query (slurp "test/data/relationship.graphql")
                           {:id sighting-1-id}
                           "RelationshipsWithoutTargetRefQueryTest")]
             (is (= 200 status))
             (is (empty? errors) "No errors")
             (is (= "indicator" (get-in data [:sighting :relationships :nodes 0
                                              :target_entity :type]))))))))))

