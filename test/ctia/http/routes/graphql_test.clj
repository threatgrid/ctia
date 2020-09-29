(ns ctia.http.routes.graphql-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [fake-whoami-service :as whoami-helpers]
             [graphql :as gh]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.casebooks :refer [new-casebook-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(def ownership-data-fixture
  {:groups ["foogroup"]
   :owner "foouser"})

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
   "timestamp" #inst "2042-01-01T00:00:00.000Z"
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
   "timestamp" #inst "2042-01-01T00:00:00.000Z"
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
   "timestamp" #inst "2042-01-01T00:00:00.000Z"
   "reason" "This is a clean IP address"
   "reason_uri" "https://panacea.threatgrid.com/somefeed"
   "valid_time" {"start_time" "2016-02-11T00:40:48.000Z"
                 "end_time" "2025-03-11T00:40:48.000Z"}})

(def indicator-1
  {"title" "Bad IP because someone said so"
   "tlp" "green"
   "producer" "someguy"
   "timestamp" #inst "2042-01-01T00:00:00.000Z"
   "description" "We heard from this guy that this IP was not to be trusted"
   "indicator_type" ["C2" "IP Watchlist"]
   "valid_time" {"start_time" "2016-05-11T00:40:48.000Z"
                 "end_time" "2025-07-11T00:40:48.000Z"}})

(def indicator-2
  {"title" "Malware detected because someone said so"
   "tlp" "green"
   "producer" "someguy"
   "timestamp" #inst "2042-01-01T00:00:00.000Z"
   "description" "We heard from this guy that a malware has been detected"
   "indicator_type" ["Malware Artifacts"]
   "valid_time" {"start_time" "2016-05-11T00:40:48.000Z"
                 "end_time" "2025-07-11T00:40:48.000Z"}})

(def indicator-3
  {"title" "Malware may modify files"
   "tlp" "green"
   "producer" "someguy"
   "timestamp" #inst "2042-01-01T00:00:00.000Z"
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
   "timestamp" #inst "2042-01-01T00:00:00.000Z"
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
   "timestamp" #inst "2042-01-01T00:00:00.000Z"
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
      (assoc :title "foo" :timestamp #inst "2042-01-01T00:00:00.000Z")
      (dissoc :id)))

(def casebook-2
  (-> new-casebook-maximal
      (assoc :title "bar" :timestamp #inst "2042-01-01T00:00:00.000Z")
      (dissoc :id)))

(defn feedback-1 [entity_id]
  {:feedback -1
   :reason "False positive"
   :timestamp #inst "2042-01-01T00:00:00.000Z"
   :entity_id entity_id})

(defn feedback-2 [entity_id]
  {:feedback 0
   :reason "Unknown"
   :timestamp #inst "2042-01-01T00:00:00.000Z"
   :entity_id entity_id})


(defn initialize-graphql-data [app]
  (let [i1  (gh/create-object app "indicator" indicator-1)
        i2  (gh/create-object app "indicator" indicator-2)
        i3  (gh/create-object app "indicator" indicator-3)
        j1  (gh/create-object app "judgement" judgement-1)
        j2  (gh/create-object app "judgement" judgement-2)
        j3  (gh/create-object app "judgement" judgement-3)
        sc1 (gh/create-object app "casebook" casebook-1)
        sc2 (gh/create-object app "casebook" casebook-2)
        s1  (gh/create-object app "sighting" sighting-1)
        s2  (gh/create-object app "sighting" sighting-2)]
    (gh/create-object app "feedback" (feedback-1 (:id i1)))
    (gh/create-object app "feedback" (feedback-2 (:id i1)))
    (gh/create-object app "feedback" (feedback-1 (:id j1)))
    (gh/create-object app "feedback" (feedback-2 (:id j1)))
    (gh/create-object app "feedback" (feedback-1 (:id s1)))
    (gh/create-object app "feedback" (feedback-2 (:id s1)))
    (gh/create-object app
                      "relationship"
                      {:relationship_type "element-of"
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :target_ref (:id i1)
                       :source_ref (:id j1)})
    (gh/create-object app
                      "relationship"
                      {:relationship_type "element-of"
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :target_ref (:id i2)
                       :source_ref (:id j1)})
    (gh/create-object app
                      "relationship"
                      {:relationship_type "element-of"
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :target_ref (:id i1)
                       :source_ref (:id j2)})
    (gh/create-object app
                      "relationship"
                      {:relationship_type "element-of"
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :target_ref (:id i1)
                       :source_ref (:id j3)})
    (gh/create-object app
                      "relationship"
                      {:relationship_type "indicates"
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :target_ref (:id i1)
                       :source_ref (:id s1)})
    (gh/create-object app
                      "relationship"
                      {:relationship_type "indicates"
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :target_ref (:id i2)
                       :source_ref (:id s1)})
    (gh/create-object app
                      "relationship"
                      {:relationship_type "variant-of"
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :target_ref (:id i2)
                       :source_ref (:id i1)})
    (gh/create-object app
                      "relationship"
                      {:relationship_type "variant-of"
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :target_ref (:id i3)
                       :source_ref (:id i1)})
    {:indicator-1 i1
     :indicator-2 i2
     :indicator-3 i3
     :judgement-1 j1
     :judgement-2 j2
     :judgement-3 j3
     :casebook-1 sc1
     :casebook-2 sc2
     :sighting-1 s1
     :sighting-2 s2}))

(deftest test-graphql-route
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (helpers/set-capabilities! app "baruser" ["bargroup"] "user" #{})
     (whoami-helpers/set-whoami-response "2222222222222" "baruser" "bargroup" "user")

     (let [datamap (initialize-graphql-data app)
           indicator-1-id (get-in datamap [:indicator-1 :id])
           indicator-2-id (get-in datamap [:indicator-2 :id])
           indicator-3-id (get-in datamap [:indicator-3 :id])
           casebook-1-id (get-in datamap [:casebook-1 :id])
           judgement-1-id (get-in datamap [:judgement-1 :id])
           sighting-1-id (get-in datamap [:sighting-1 :id])
           graphql-queries (slurp "test/data/queries.graphql")]

       (testing "POST /ctia/graphql"

         (testing "Query syntax error"
           (let [{:keys [_ errors status]} (gh/query app "dummy" {} "")]
             (is (= 400 status))
             (is (= errors
                    ["InvalidSyntaxError{ message=Invalid Syntax ,locations=[SourceLocation{line=1, column=0}]}"]))))

         (testing "Query validation error"
           (let [{:keys [_ errors status]}
                 (gh/query app
                           "query TestQuery { nonexistent }"
                           {}
                           "TestQuery")]
             (is (= 400 status))
             (is (= errors
                    '("ValidationError{validationErrorType=FieldUndefined, queryPath=[nonexistent], message=Validation error of type FieldUndefined: Field 'nonexistent' in type 'Root' is undefined @ 'nonexistent', locations=[SourceLocation{line=1, column=19}], description='Field 'nonexistent' in type 'Root' is undefined'}")))))
         (testing "unauthorized access without capabilities"
           (let [{:keys [status]}
                 (helpers/POST app
                               "ctia/graphql"
                               :body {:query ""
                                      :variables {}
                                      :operationName ""}
                               :headers {"Authorization" "2222222222222"})]
             (is (= 403 status))))
         (testing "observable query"
           (let [{:keys [data errors status]}
                 (gh/query app
                           graphql-queries
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
               (gh/connection-test app
                                   "ObservableQueryTest"
                                   graphql-queries
                                   {:type "ip"
                                    :value "1.2.3.4"}
                                   [:observable :judgements]
                                   [(:judgement-1 datamap)
                                    (:judgement-2 datamap)])

               (testing "sorting"
                 (gh/connection-sort-test
                  app
                  "ObservableQueryTest"
                  graphql-queries
                  {:type "ip"
                   :value "1.2.3.4"}
                  [:observable :judgements]
                  ctia.entity.judgement.schemas/judgement-fields)))))

         (testing "judgement query"
           (let [{:keys [data errors status]}
                 (gh/query app
                           graphql-queries
                           {:id judgement-1-id}
                           "JudgementQueryTest")]
             (is (= 200 status))
             (is (empty? errors) "No errors")

             (testing "the judgement"
               (is (= (:judgement-1 datamap)
                      (dissoc (:judgement data)
                              :relationships))))

             (testing "relationships connection"
               (gh/connection-test app
                                   "JudgementQueryTest"
                                   graphql-queries
                                   {:id judgement-1-id}
                                   [:judgement :relationships]
                                   (map
                                    #(merge % ownership-data-fixture)
                                    [{:relationship_type "element-of"
                                      :target_ref indicator-1-id
                                      :source_ref judgement-1-id
                                      :source_entity (:judgement-1 datamap)
                                      :target_entity (:indicator-1 datamap)
                                      :timestamp #inst "2042-01-01T00:00:00.000Z"}
                                     {:relationship_type "element-of"
                                      :target_ref indicator-2-id
                                      :source_ref judgement-1-id
                                      :source_entity (:judgement-1 datamap)
                                      :target_entity (:indicator-2 datamap)
                                      :timestamp #inst "2042-01-01T00:00:00.000Z"}]))

               (testing "sorting"
                 (gh/connection-sort-test
                  app
                  "JudgementQueryTest"
                  graphql-queries
                  {:id judgement-1-id}
                  [:judgement :relationships]
                  ctia.entity.relationship/relationship-fields)))

             (testing "feedbacks connection"
               (gh/connection-test app
                                   "JudgementFeedbacksQueryTest"
                                   graphql-queries
                                   {:id judgement-1-id}
                                   [:judgement :feedbacks]
                                   (map #(merge % ownership-data-fixture)
                                        [(feedback-1 judgement-1-id)
                                         (feedback-2 judgement-1-id)]))

               (testing "sorting"
                 (gh/connection-sort-test
                  app
                  "JudgementFeedbacksQueryTest"
                  graphql-queries
                  {:id judgement-1-id}
                  [:judgement :feedbacks]
                  ctia.entity.feedback.schemas/feedback-fields)))))

         (testing "judgements query"
           (testing "judgements connection"
             (gh/connection-test app
                                 "JudgementsQueryTest"
                                 graphql-queries
                                 {:query "*"}
                                 [:judgements]
                                 (map #(merge % ownership-data-fixture)
                                      [(:judgement-1 datamap)
                                       (:judgement-2 datamap)
                                       (:judgement-3 datamap)]))

             (testing "sorting"
               (gh/connection-sort-test
                app
                "JudgementsQueryTest"
                graphql-queries
                {:query "*"}
                [:judgements]
                ctia.entity.judgement.schemas/judgement-fields)))

           (testing "query argument"
             (let [{:keys [data errors status]}
                   (gh/query app
                             graphql-queries
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
                 (gh/query app
                           graphql-queries
                           {:id indicator-1-id}
                           "IndicatorQueryTest")]
             (is (= 200 status))
             (is (empty? errors) "No errors")

             (testing "the indicator"
               (is (= (:indicator-1 datamap)
                      (dissoc (:indicator data)
                              :relationships))))

             (testing "relationships connection"
               (gh/connection-test app
                                   "IndicatorQueryTest"
                                   graphql-queries
                                   {:id indicator-1-id}
                                   [:indicator :relationships]
                                   (map #(merge % ownership-data-fixture)
                                        [{:relationship_type "variant-of"
                                          :target_ref indicator-2-id
                                          :source_ref indicator-1-id
                                          :source_entity (:indicator-1 datamap)
                                          :target_entity (:indicator-2 datamap)
                                          :timestamp #inst "2042-01-01T00:00:00.000Z"}
                                         {:relationship_type "variant-of"
                                          :target_ref indicator-3-id
                                          :source_ref indicator-1-id
                                          :source_entity (:indicator-1 datamap)
                                          :target_entity (:indicator-3 datamap)
                                          :timestamp #inst "2042-01-01T00:00:00.000Z"}]))

               (testing "sorting"
                 (gh/connection-sort-test
                  app
                  "IndicatorQueryTest"
                  graphql-queries
                  {:id indicator-1-id}
                  [:indicator :relationships]
                  ctia.entity.relationship/relationship-fields)))

             (testing "feedbacks connection"
               (gh/connection-test app
                                   "IndicatorFeedbacksQueryTest"
                                   graphql-queries
                                   {:id indicator-1-id}
                                   [:indicator :feedbacks]
                                   (map #(merge % ownership-data-fixture)
                                        [(feedback-1 indicator-1-id)
                                         (feedback-2 indicator-1-id)]))

               (testing "sorting"
                 (gh/connection-sort-test
                  app
                  "IndicatorFeedbacksQueryTest"
                  graphql-queries
                  {:id indicator-1-id}
                  [:indicator :feedbacks]
                  ctia.entity.feedback.schemas/feedback-fields)))))

         (testing "indicators query"
           (testing "indicators connection"
             (gh/connection-test app
                                 "IndicatorsQueryTest"
                                 graphql-queries
                                 {"query" "*"}
                                 [:indicators]
                                 (map #(merge % ownership-data-fixture)
                                      [(:indicator-1 datamap)
                                       (:indicator-2 datamap)
                                       (:indicator-3 datamap)]))

             (testing "sorting"
               (gh/connection-sort-test
                app
                "IndicatorsQueryTest"
                graphql-queries
                {:query "*"}
                [:indicators]
                ctia.entity.indicator/indicator-fields)))

           (testing "query argument"
             (let [{:keys [data errors status]}
                   (gh/query app
                             graphql-queries
                             {:query "indicator_type:\"C2\""}
                             "IndicatorsQueryTest")]
               (is (= 200 status))
               (is (empty? errors) "No errors")
               (is (= 1 (get-in data [:indicators :totalCount]))
                   "Only one indicator matches to the query")
               (is (= [(:indicator-1 datamap)]
                      (get-in data [:indicators :nodes]))
                   "The indicator matches the search query"))))

         (testing "casebook query"
           (let [{:keys [data errors status]}
                 (gh/query app
                           graphql-queries
                           {:id casebook-1-id}
                           "CasebookQueryTest")]

             (is (= 200 status))
             (is (empty? errors) "No errors")

             (testing "the casebook"
               (is (= (dissoc (:casebook-1 datamap) :bundle :texts)
                      (:casebook data))))))

         (testing "casebooks query"
           (testing "casebooks connection"
             (gh/connection-test app
                                 "CasebooksQueryTest"
                                 graphql-queries
                                 {"query" "*"}
                                 [:casebooks]
                                 (map #(merge % ownership-data-fixture)
                                      [(dissoc (:casebook-1 datamap) :bundle :texts)
                                       (dissoc (:casebook-2 datamap) :bundle :texts)]))

             (testing "sorting"
               (gh/connection-sort-test
                app
                "CasebooksQueryTest"
                graphql-queries
                {:query "*"}
                [:casebooks]
                ctia.entity.casebook/casebook-fields)))

           (testing "query argument"
             (let [{:keys [data errors status]}
                   (gh/query app
                             graphql-queries
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
                 (gh/query app
                           graphql-queries
                           {:id (get-in datamap [:sighting-1 :id])}
                           "SightingQueryTest")]
             (is (= 200 status))
             (is (empty? errors) "No errors")

             (testing "the sighting"
               (is (= (:sighting-1 datamap)
                      (-> (:sighting data)
                          (dissoc :relationships)))))

             (testing "relationships connection"
               (gh/connection-test app
                                   "SightingQueryTest"
                                   graphql-queries
                                   {:id sighting-1-id}
                                   [:sighting :relationships]
                                   (map #(merge % ownership-data-fixture)
                                        [{:relationship_type "indicates"
                                          :target_ref indicator-1-id
                                          :source_ref sighting-1-id
                                          :source_entity (:sighting-1 datamap)
                                          :target_entity (:indicator-1 datamap)
                                          :timestamp #inst "2042-01-01T00:00:00.000Z"}
                                         {:relationship_type "indicates"
                                          :target_ref indicator-2-id
                                          :source_ref sighting-1-id
                                          :source_entity (:sighting-1 datamap)
                                          :target_entity (:indicator-2 datamap)
                                          :timestamp #inst "2042-01-01T00:00:00.000Z"}]))

               (testing "sorting"
                 (gh/connection-sort-test
                  app
                  "SightingQueryTest"
                  graphql-queries
                  {:id sighting-1-id}
                  [:sighting :relationships]
                  ctia.entity.relationship/relationship-fields)))

             (testing "feedbacks connection"
               (gh/connection-test app
                                   "SightingFeedbacksQueryTest"
                                   graphql-queries
                                   {:id sighting-1-id}
                                   [:sighting :feedbacks]
                                   (map #(merge % ownership-data-fixture)
                                        [(feedback-1 sighting-1-id)
                                         (feedback-2 sighting-1-id)]))

               (testing "sorting"
                 (gh/connection-sort-test
                  app
                  "SightingFeedbacksQueryTest"
                  graphql-queries
                  {:id sighting-1-id}
                  [:sighting :feedbacks]
                  ctia.entity.feedback.schemas/feedback-fields)))))

         (testing "sightings query"

           (testing "sightings connection"
             (gh/connection-test app
                                 "SightingsQueryTest"
                                 graphql-queries
                                 {"query" "*"}
                                 [:sightings]
                                 (map #(merge % ownership-data-fixture)
                                      [(:sighting-1 datamap)
                                       (:sighting-2 datamap)]))

             (testing "sorting"
               (gh/connection-sort-test
                app
                "SightingsQueryTest"
                graphql-queries
                {:query "*"}
                [:sightings]
                ctia.entity.sighting.schemas/sighting-fields)))

           (testing "query argument"
             (let [{:keys [data errors status]}
                   (gh/query app
                             graphql-queries
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
                 (gh/query app
                           (slurp "test/data/relationship.graphql")
                           {:id sighting-1-id}
                           "RelationshipsWithoutTargetRefQueryTest")]
             (is (= 200 status))
             (is (empty? errors) "No errors")
             (is (= "indicator" (get-in data [:sighting :relationships :nodes 0
                                              :target_entity :type]))))))))))

