(ns ctia.http.routes.graphql.attack-pattern-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.schemas.sorting :as sort-fields]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [fake-whoami-service :as whoami-helpers]
             [graphql :as gh]
             [store :refer [test-for-each-store]]]
            [ctim.examples.attack-patterns :refer [new-attack-pattern-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn init-graph-data []
  (let [ap1 (gh/create-object
             "attack-pattern"
             (-> new-attack-pattern-maximal
                 (assoc :name "Attack Pattern 1")
                 (dissoc :id)))
        ap2 (gh/create-object
             "attack-pattern"
             (-> new-attack-pattern-maximal
                 (assoc :name "Attack Pattern 2")
                 (dissoc :id)))
        ap3 (gh/create-object
             "attack-pattern"
             (-> new-attack-pattern-maximal
                 (assoc :name "Attack Pattern 3")
                 (dissoc :id)))]
    (gh/create-object "feedback" (gh/feedback-1 (:id ap1)))
    (gh/create-object "feedback" (gh/feedback-2 (:id ap1)))
    (gh/create-object "relationship"
                      {:relationship_type "variant-of"
                       :target_ref (:id ap2)
                       :source_ref (:id ap1)})
    (gh/create-object "relationship"
                      {:relationship_type "variant-of"
                       :target_ref (:id ap3)
                       :source_ref (:id ap1)})
    {:attack-pattern-1 ap1
     :attack-pattern-2 ap2
     :attack-pattern-3 ap3}))

(deftest attack-pattern-queries-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [datamap (init-graph-data)
           attack-pattern-1-id (get-in datamap [:attack-pattern-1 :id])
           attack-pattern-2-id (get-in datamap [:attack-pattern-2 :id])
           attack-pattern-3-id (get-in datamap [:attack-pattern-3 :id])
           graphql-queries (str (slurp "test/data/attack-pattern.graphql")
                                (slurp "test/data/fragments.graphql"))]

       (testing "attack_pattern query"
         (let [{:keys [data errors status]}
               (gh/query graphql-queries
                         {:id (get-in datamap [:attack-pattern-1 :id])}
                         "AttackPatternQueryTest")]
           (is (= 200 status))
           (is (empty? errors) "No errors")

           (testing "the attack pattern"
             (is (= (:attack-pattern-1 datamap)
                    (-> (:attack_pattern data)
                        (dissoc :relationships)))))

           (testing "relationships connection"
             (gh/connection-test "AttackPatternQueryTest"
                                 graphql-queries
                                 {:id attack-pattern-1-id
                                  :relationship_type "variant-of"}
                                 [:attack_pattern :relationships]
                                 [{:relationship_type "variant-of"
                                   :target_ref attack-pattern-2-id
                                   :source_ref attack-pattern-1-id
                                   :source_entity (:attack-pattern-1 datamap)
                                   :target_entity (:attack-pattern-2 datamap)}
                                  {:relationship_type "variant-of"
                                   :target_ref attack-pattern-3-id
                                   :source_ref attack-pattern-1-id
                                   :source_entity (:attack-pattern-1 datamap)
                                   :target_entity (:attack-pattern-3 datamap)}])

             (testing "sorting"
               (gh/connection-sort-test
                "AttackPatternQueryTest"
                graphql-queries
                {:id attack-pattern-1-id}
                [:attack_pattern :relationships]
                ctia.entity.relationship.schemas/relationship-fields)))

           (testing "feedbacks connection"
             (gh/connection-test "AttackPatternFeedbacksQueryTest"
                                 graphql-queries
                                 {:id attack-pattern-1-id}
                                 [:attack_pattern :feedbacks]
                                 [(gh/feedback-1 attack-pattern-1-id)
                                  (gh/feedback-2 attack-pattern-1-id)])

             (testing "sorting"
               (gh/connection-sort-test
                "AttackPatternFeedbacksQueryTest"
                graphql-queries
                {:id attack-pattern-1-id}
                [:attack_pattern :feedbacks]
                ctia.entity.feedback.schemas/feedback-fields))))
         (testing "attack_patterns query"
           (testing "attack_patterns connection"
             (gh/connection-test "AttackPatternsQueryTest"
                                 graphql-queries
                                 {"query" "*"}
                                 [:attack_patterns]
                                 [(:attack-pattern-1 datamap)
                                  (:attack-pattern-2 datamap)
                                  (:attack-pattern-3 datamap)])

             (testing "sorting"
               (gh/connection-sort-test
                "AttackPatternsQueryTest"
                graphql-queries
                {:query "*"}
                [:attack_patterns]
                ctia.entity.attack-pattern/attack-pattern-fields)))

           (testing "query argument"
             (let [{:keys [data errors status]}
                   (gh/query graphql-queries
                             {:query (format "name:\"%s\""
                                             (get-in
                                              datamap
                                              [:attack-pattern-1 :name]))}
                             "AttackPatternsQueryTest")]
               (is (= 200 status))
               (is (empty? errors) "No errors")
               (is (= 1 (get-in data [:attack_patterns :totalCount]))
                   "Only one attack pattern matches to the query")
               (is (= (:attack-pattern-1 datamap)
                      (first (get-in data [:attack_patterns :nodes])))
                   "The attack pattern matches the search query")))))))))
