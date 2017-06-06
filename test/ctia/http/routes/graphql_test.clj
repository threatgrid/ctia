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
            [ctim.domain.id :as id]))

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
   "valid_time" {"start_time" "2016-05-11T00:40:48Z"
                 "end_time" "2025-07-11T00:40:48Z"}})


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
        j1 (create-object "judgement" judgement-1)
        j2 (create-object "judgement" judgement-2)
        j3 (create-object "judgement" judgement-3)]
    (create-object "relationship"
                      {:relationship_type "element-of"
                       :target_ref (:id i1)
                       :source_ref (:id j1)})
    (create-object "relationship"
                   {:relationship_type "element-of"
                    :target_ref (:id i1)
                    :source_ref (:id j2)})
    (create-object "relationship"
                   {:relationship_type "element-of"
                    :target_ref (:id i1)
                    :source_ref (:id j3)})
    {:indicator-1 i1
     :judgement-1 j1
     :judgmeent-2 j2
     :judgement-3 j3}))


(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

;;ctia.test-helpers.core/fixture-properties:events-enabled

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-graphql-route
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")
  (let [datamap (initialize-graphql-data)]
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
        (testing "without variables"
          (let [{status :status
                 body :parsed-body}
                (helpers/post "ctia/graphql"
                      :body {:query "query TestQuery {
                                 observable(type: \"ip\" value: \"1.2.3.4\") {
                                   value
                                   verdict { type disposition_name disposition
                                             judgement { id type external_ids tlp disposition disposition_name priority confidence severity reason reason_uri valid_time { start_time end_time } observable { value type } source source_uri } }
                                   judgements(first: 1) { totalCount pageInfo { endCursor hasNextPage } edges { node { reason type source id } } }
                                 }
                               }"}
                      :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 200 status))
            (is (empty? (:errors body))
                "No errors")
            (is (deep= (get-in body [:data "observable" "value"]) "1.2.3.4"))
            (testing "the verdict"
              (let [verdict (get-in body [:data "observable" "verdict"])]
                (is (not (empty? verdict))
                    "exists")
                (is (= (get verdict "disposition") 2)
                    "has correct disposition")
                (is (= (get verdict "disposition_name") "Malicious")
                    "has correct disposition_name")
                (is (= (get verdict "type") "verdict")
                    "has correct type")
                (testing "the judgement"
                  (let [judgement (get verdict "judgement")]
                    (is (not (empty? judgement))
                        "exits")
                    (is (deep= (dissoc judgement "type" "id")
                               judgement-1)
                        "matches the judgement-2")
                    (is (= (get judgement "type") "judgement")
                        "has correct type")
                    (is (and (string? (get judgement "id"))
                             (re-matches #"http.*" (get judgement "id")))
                        "has an id")
                    (is (= (get judgement "type") "judgement")
                        "has a type")
                    ))))

            (testing "the judgement connection"
              (is (deep= (get-in body [:data "observable" "judgements" "totalCount"]) 2)
                  "judgement Connection pageInfo is correct")
              (is (deep= (get-in body [:data "observable" "judgements" "pageInfo" "hasNextPage"]) true)
                  "judgement Connection pageInfo.hasNextPage is correct")
              (is (deep= (get-in (first (get-in body [:data "observable" "judgements" "edges"])) ["node" "type"])
                         "judgement")
                  "judgement Connection contains a judgement"
                  )))))



      (testing "judgement query"
        (testing "without variables"
          (let [{status :status
                 body :parsed-body}
                (helpers/post "ctia/graphql"
                              :body {:query
                                     (str "query TestQuery {\n"
                                          " judgement(id: \"" (get-in datamap [:judgement-1 :id]) "\") { \n"
                                          "   id type external_ids tlp disposition disposition_name priority confidence \n"
                                          "   severity reason reason_uri\n"
                                          "   valid_time { start_time end_time }\n"
                                          "   observable { value type }\n"
                                          "   source source_uri\n"
                                          "   relationships {\n"
                                          "     totalCount"
                                          "     pageInfo { hasNextPage }\n"
                                          "       edges { node {\n"
                                          "                 target_ref"
                                          "               }\n"
                                          "       }\n"
                                          "   }\n"
                                          " }\n"
                                          "}")}
                      :headers {"api_key" "45c1f5e3f05d0"}
                      )]
            (is (= 200 status))
            (is (empty? (:errors body))
                "No errors")
            (is (= (get-in body [:data "judgement" "observable" "value"]) "1.2.3.4"))
            (testing "the judgement"
              (let [judgement (get-in body [:data "judgement"])]
                (is (not (empty? judgement))
                    "exits")
                (is (deep= (dissoc judgement "id" "type" "relationships")
                           judgement-1)
                    "matches the judgement-1")
                (is (= (get judgement "type") "judgement")
                    "has correct type")
                (is (and (string? (get judgement "id"))
                         (re-matches #"http.*" (get judgement "id")))
                    "has an id")
                (is (= (get judgement "type") "judgement")
                    "has a type")

                (is (= (get-in (first (get-in judgement ["relationships" "edges"])) ["node" "target_ref"])
                       (get-in datamap [:indicator-1 :id])))
                ))))



        (testing "relationship connection"

          (testing "edge and node"
            (let [{status :status
                   body :parsed-body}
                  (helpers/post "ctia/graphql"
                                :body {:query
                                       (str "query TestQuery {\n"
                                            " judgement(id: \"" (get-in datamap [:judgement-1 :id]) "\") { \n"
                                            "   relationships(relationship_type: \"element-of\") {\n"
                                            "     edges {\n"
                                            "       node {\n"
                                            "         id\n"
                                            "         target{\n "
                                            "           ... on Indicator { title }\n"
                                            "         }\n"
                                            "       }\n"
                                            "     }\n"
                                            "   }\n"
                                            " }\n"
                                            "}")}
                                :headers {"api_key" "45c1f5e3f05d0"}
                                )]
              (is (= 200 status))
              (is (empty? (:errors body))
                  "No errors")
              (is (= (get-in (first (get-in body [:data "judgement" "relationships" "edges"]))
                             ["node" "target" "title"])
                     "Bad IP because someone said so")
                  "node and it's target are correct"))

            (let [{status :status
                   body :parsed-body}
                  (helpers/post "ctia/graphql"
                                :body {:query
                                       (str "query TestQuery {\n"
                                            " judgement(id: \"" (get-in datamap [:judgement-1 :id]) "\") { \n"
                                            "   relationships(relationship_type: \"element-of\") {\n"
                                            "     totalCount\n"
                                            "   }\n"
                                            " }\n"
                                            "}")}
                                :headers {"api_key" "45c1f5e3f05d0"}
                                )]
              (is (= 200 status))
              (is (empty? (:errors body))
                  "No errors")
              (is (= (get-in body [:data "judgement" "relationships" "totalCount"])
                     1)
                  "matches element-of relationship")))

          (testing "type argument"
            (let [{status :status
                   body :parsed-body}
                  (helpers/post "ctia/graphql"
                                :body {:query
                                       (str "query TestQuery {\n"
                                            " judgement(id: \"" (get-in datamap [:judgement-1 :id]) "\") { \n"
                                            "   relationships(relationship_type: \"indicates\") {\n"
                                            "     totalCount \n"
                                            "   }\n"
                                            " }\n"
                                            "}")}
                                :headers {"api_key" "45c1f5e3f05d0"}
                                )]
              (is (= 200 status))
              (is (empty? (:errors body))
                  "No errors")
              (is (= (get-in body [:data "judgement" "relationships" "totalCount"])
                     0)
                  "does not match element-of relationship"))

            (let [{status :status
                   body :parsed-body}
                  (helpers/post "ctia/graphql"
                                :body {:query
                                       (str "query TestQuery {\n"
                                            " judgement(id: \"" (get-in datamap [:judgement-1 :id]) "\") { \n"
                                            "   relationships(relationship_type: \"element-of\") {\n"
                                            "     totalCount \n"
                                            "   }\n"
                                            " }\n"
                                            "}")}
                                :headers {"api_key" "45c1f5e3f05d0"}
                                )]
              (is (= 200 status))
              (is (empty? (:errors body))
                  "No errors")
              (is (= (get-in body [:data "judgement" "relationships" "totalCount"])
                     1)
                  "matches element-of relationship"))))))))
