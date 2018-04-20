(ns ctia.http.routes.graphql.tool-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.schemas.sorting :as sort-fields]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [fake-whoami-service :as whoami-helpers]
             [graphql :as gh]
             [store :refer [test-for-each-store]]]
            [ctim.examples.tools :refer [new-tool-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn init-graph-data []
  (let [entity-1 (gh/create-object
                  "tool"
                  (-> new-tool-maximal
                      (assoc :name "Tool 1")
                      (dissoc :id)))
        entity-2 (gh/create-object
                  "tool"
                  (-> new-tool-maximal
                      (assoc :name "Tool 2")
                      (dissoc :id)))
        entity-3 (gh/create-object
                  "tool"
                  (-> new-tool-maximal
                      (assoc :name "Tool 3")
                      (dissoc :id)))]
    (gh/create-object "feedback" (gh/feedback-1 (:id entity-1)))
    (gh/create-object "feedback" (gh/feedback-2 (:id entity-1)))
    (gh/create-object "relationship"
                      {:relationship_type "variant-of"
                       :target_ref (:id entity-2)
                       :source_ref (:id entity-1)})
    (gh/create-object "relationship"
                      {:relationship_type "variant-of"
                       :target_ref (:id entity-3)
                       :source_ref (:id entity-1)})
    {:tool-1 entity-1
     :tool-2 entity-2
     :tool-3 entity-3}))

(deftest tool-queries-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [datamap (init-graph-data)
           tool-1-id (get-in datamap [:tool-1 :id])
           tool-2-id (get-in datamap [:tool-2 :id])
           tool-3-id (get-in datamap [:tool-3 :id])
           graphql-queries (str (slurp "test/data/tool.graphql")
                                (slurp "test/data/fragments.graphql"))]

       (testing "tool query"
         (let [{:keys [data errors status]}
               (gh/query graphql-queries
                         {:id (get-in datamap [:tool-1 :id])}
                         "ToolQueryTest")]
           (is (= 200 status))
           (is (empty? errors) "No errors")

           (testing "the tool"
             (is (= (:tool-1 datamap)
                    (-> (:tool data)
                        (dissoc :relationships)))))

           (testing "relationships connection"
             (gh/connection-test "ToolQueryTest"
                                 graphql-queries
                                 {:id tool-1-id
                                  :relationship_type "variant-of"}
                                 [:tool :relationships]
                                 [{:relationship_type "variant-of"
                                   :target_ref tool-2-id
                                   :source_ref tool-1-id
                                   :source_entity (:tool-1 datamap)
                                   :target_entity (:tool-2 datamap)}
                                  {:relationship_type "variant-of"
                                   :target_ref tool-3-id
                                   :source_ref tool-1-id
                                   :source_entity (:tool-1 datamap)
                                   :target_entity (:tool-3 datamap)}])

             (testing "sorting"
               (gh/connection-sort-test
                "ToolQueryTest"
                graphql-queries
                {:id tool-1-id}
                [:tool :relationships]
                ctia.entity.relationship.schemas/relationship-fields)))

           (testing "feedbacks connection"
             (gh/connection-test "ToolFeedbacksQueryTest"
                                 graphql-queries
                                 {:id tool-1-id}
                                 [:tool :feedbacks]
                                 [(gh/feedback-1 tool-1-id)
                                  (gh/feedback-2 tool-1-id)])

             (testing "sorting"
               (gh/connection-sort-test
                "ToolFeedbacksQueryTest"
                graphql-queries
                {:id tool-1-id}
                [:tool :feedbacks]
                ctia.entity.feedback.schemas/feedback-fields))))
         (testing "tools query"
           (testing "tools connection"
             (gh/connection-test "ToolsQueryTest"
                                 graphql-queries
                                 {"query" "*"}
                                 [:tools]
                                 [(:tool-1 datamap)
                                  (:tool-2 datamap)
                                  (:tool-3 datamap)])

             (testing "sorting"
               (gh/connection-sort-test
                "ToolsQueryTest"
                graphql-queries
                {:query "*"}
                [:tools]
                ctia.entity.tool.schemas/tool-fields)))

           (testing "query argument"
             (let [{:keys [data errors status]}
                   (gh/query graphql-queries
                             {:query (format "name:\"%s\""
                                             (get-in
                                              datamap
                                              [:tool-1 :name]))}
                             "ToolsQueryTest")]
               (is (= 200 status))
               (is (empty? errors) "No errors")
               (is (= 1 (get-in data [:tools :totalCount]))
                   "Only one tool matches to the query")
               (is (= (:tool-1 datamap)
                      (first (get-in data [:tools :nodes])))
                   "The tool matches the search query")))))))))


