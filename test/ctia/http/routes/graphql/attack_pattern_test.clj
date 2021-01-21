(ns ctia.http.routes.graphql.attack-pattern-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [fake-whoami-service :as whoami-helpers]
             [graphql :as gh]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.attack-patterns :refer [new-attack-pattern-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(def external-ref "http://external.com/ctia/attack-pattern/attack-pattern-ab053333-2ad2-41d0-a445-31e9b9c38caf")

(def ownership-data-fixture
  {:owner "foouser"
   :groups ["foogroup"]})

(defn init-graph-data [app]
  (let [ap1 (gh/create-object
             app
             "attack-pattern"
             (-> new-attack-pattern-maximal
                 (assoc :title "Attack Pattern 1")
                 (dissoc :id)))
        ap2 (gh/create-object
             app
             "attack-pattern"
             (-> new-attack-pattern-maximal
                 (assoc :title "Attack Pattern 2")
                 (dissoc :id)))
        ap3 (gh/create-object
             app
             "attack-pattern"
             (-> new-attack-pattern-maximal
                 (assoc :title "Attack Pattern 3")
                 (dissoc :id)))
        f1 (gh/create-object app
                             "feedback"
                             (gh/feedback-1 (:id ap1) #inst "2042-01-01T00:00:00.000Z"))
        f2 (gh/create-object app
                             "feedback"
                             (gh/feedback-2 (:id ap1) #inst "2042-01-01T00:00:00.000Z"))]
    (gh/create-object app
                      "relationship"
                      {:relationship_type "variant-of"
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :target_ref (:id ap2)
                       :source_ref (:id ap1)})
    (gh/create-object app
                      "relationship"
                      {:relationship_type "variant-of"
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :target_ref (:id ap3)
                       :source_ref (:id ap1)})
    (gh/create-object app
                      "relationship"
                      {:relationship_type "variant-of"
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :target_ref external-ref
                       :source_ref (:id ap1)})
    {:attack-pattern-1 ap1
     :attack-pattern-2 ap2
     :attack-pattern-3 ap3
     :feedback-1 f1
     :feedback-2 f2}))

(deftest attack-pattern-queries-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [datamap (init-graph-data app)
           attack-pattern-1-id (get-in datamap [:attack-pattern-1 :id])
           attack-pattern-2-id (get-in datamap [:attack-pattern-2 :id])
           attack-pattern-3-id (get-in datamap [:attack-pattern-3 :id])
           graphql-queries (str (slurp "test/data/attack-pattern.graphql")
                                (slurp "test/data/fragments.graphql"))]

       (testing "attack_pattern query"
         (let [{:keys [data errors status]}
               (gh/query app
                         graphql-queries
                         {:id (get-in datamap [:attack-pattern-1 :id])}
                         "AttackPatternQueryTest")]
           (is (= 200 status))
           (is (empty? errors) "No errors")

           (testing "the attack pattern"
             (is (= (:attack-pattern-1 datamap)
                    (-> (:attack_pattern data)
                        (dissoc :relationships)))))

           (testing "relationships connection"
             (gh/connection-test app
                                 "AttackPatternQueryTest"
                                 graphql-queries
                                 {:id attack-pattern-1-id
                                  :relationship_type "variant-of"}
                                 [:attack_pattern :relationships]
                                 (map #(merge % ownership-data-fixture)
                                      [{:relationship_type "variant-of"
                                        :target_ref attack-pattern-2-id
                                        :source_ref attack-pattern-1-id
                                        :timestamp #inst "2042-01-01T00:00:00.000Z"
                                        :source_entity (:attack-pattern-1 datamap)
                                        :target_entity (:attack-pattern-2 datamap)}
                                       {:relationship_type "variant-of"
                                        :target_ref attack-pattern-3-id
                                        :source_ref attack-pattern-1-id
                                        :timestamp #inst "2042-01-01T00:00:00.000Z"
                                        :source_entity (:attack-pattern-1 datamap)
                                        :target_entity (:attack-pattern-3 datamap)}
                                       {:relationship_type "variant-of"
                                        :target_ref external-ref
                                        :source_ref attack-pattern-1-id
                                        :timestamp #inst "2042-01-01T00:00:00.000Z"
                                        :source_entity (:attack-pattern-1 datamap)
                                        :target_entity nil}]))

             (testing "sorting"
               (gh/connection-sort-test
                app
                "AttackPatternQueryTest"
                graphql-queries
                {:id attack-pattern-1-id}
                [:attack_pattern :relationships]
                ctia.entity.relationship.schemas/relationship-fields)))

           (testing "feedbacks connection"
             (gh/connection-test app
                                 "AttackPatternFeedbacksQueryTest"
                                 graphql-queries
                                 {:id attack-pattern-1-id}
                                 [:attack_pattern :feedbacks]
                                 [(dissoc (:feedback-1 datamap)
                                          :id
                                          :tlp
                                          :schema_version
                                          :type)
                                  (dissoc (:feedback-2 datamap)
                                          :id
                                          :tlp
                                          :schema_version
                                          :type)])

             (testing "sorting"
               (gh/connection-sort-test
                app
                "AttackPatternFeedbacksQueryTest"
                graphql-queries
                {:id attack-pattern-1-id}
                [:attack_pattern :feedbacks]
                ctia.entity.feedback.schemas/feedback-fields))))
         (testing "attack_patterns query"
           (testing "attack_patterns connection"
             (gh/connection-test app
                                 "AttackPatternsQueryTest"
                                 graphql-queries
                                 {"query" "*"}
                                 [:attack_patterns]
                                 [(:attack-pattern-1 datamap)
                                  (:attack-pattern-2 datamap)
                                  (:attack-pattern-3 datamap)])

             (testing "sorting"
               (gh/connection-sort-test
                app
                "AttackPatternsQueryTest"
                graphql-queries
                {:query "*"}
                [:attack_patterns]
                ctia.entity.attack-pattern/attack-pattern-fields)))

           (testing "query argument"
             (let [{:keys [data errors status]}
                   (gh/query app
                             graphql-queries
                             {:query (format "title:\"%s\""
                                             (get-in
                                              datamap
                                              [:attack-pattern-1 :title]))}
                             "AttackPatternsQueryTest")]
               (is (= 200 status))
               (is (empty? errors) "No errors")
               (is (= 1 (get-in data [:attack_patterns :totalCount]))
                   "Only one attack pattern matches to the query")
               (is (= (:attack-pattern-1 datamap)
                      (first (get-in data [:attack_patterns :nodes])))
                   "The attack pattern matches the search query")))))))))
