(ns ctia.http.routes.graphql.incident-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.entity.incident.schemas :refer [incident-fields]]
            [ctia.entity.feedback.schemas :refer [feedback-fields]]
            [ctia.entity.relationship.schemas :refer [relationship-fields]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [fake-whoami-service :as whoami-helpers]
             [graphql :as gh]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.incidents :refer [new-incident-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(def ownership-data-fixture
  {:owner "foouser"
   :groups ["foogroup"]})

(def external-ref "http://external.com/ctia/incident/incident-ab053333-2ad2-41d0-a445-31e9b9c38caf")

(defn init-graph-data [app]
  (let [ap1 (gh/create-object
             app
             "incident"
             (-> new-incident-maximal
                 (assoc :title "Incident 1")
                 (dissoc :id)))
        ap2 (gh/create-object
             app
             "incident"
             (-> new-incident-maximal
                 (assoc :title "Incident 2")
                 (dissoc :id)))
        ap3 (gh/create-object
             app
             "incident"
             (-> new-incident-maximal
                 (assoc :title "Incident 3")
                 (dissoc :id)))
        f1 (gh/create-object app "feedback" (gh/feedback-1 (:id ap1) #inst "2042-01-01T00:00:00.000Z"))
        f2 (gh/create-object app "feedback" (gh/feedback-2 (:id ap1) #inst "2042-01-01T00:00:00.000Z"))]
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
    {:incident-1 ap1
     :incident-2 ap2
     :incident-3 ap3
     :feedback-1 f1
     :feedback-2 f2}))

(deftest incident-queries-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [datamap (init-graph-data app)
           incident-1-id (get-in datamap [:incident-1 :id])
           incident-2-id (get-in datamap [:incident-2 :id])
           incident-3-id (get-in datamap [:incident-3 :id])
           graphql-queries (str (slurp "test/data/incident.graphql")
                                (slurp "test/data/fragments.graphql"))]

       (testing "incident query"
         (let [{:keys [data errors status]}
               (gh/query app
                         graphql-queries
                         {:id (get-in datamap [:incident-1 :id])}
                         "IncidentQueryTest")]
           (is (= 200 status))
           (is (empty? errors) "No errors")

           (testing "the incident"
             (is (= (:incident-1 datamap)
                    (-> (:incident data)
                        (dissoc :relationships)))))

           (testing "relationships connection"
             (gh/connection-test app
                                 "IncidentQueryTest"
                                 graphql-queries
                                 {:id incident-1-id
                                  :relationship_type "variant-of"}
                                 [:incident :relationships]
                                 (map #(merge % ownership-data-fixture)
                                      [{:relationship_type "variant-of"
                                        :target_ref incident-2-id
                                        :source_ref incident-1-id
                                        :source_entity (:incident-1 datamap)
                                        :target_entity (:incident-2 datamap)
                                        :timestamp #inst "2042-01-01T00:00:00.000Z"}
                                       {:relationship_type "variant-of"
                                        :target_ref incident-3-id
                                        :source_ref incident-1-id
                                        :source_entity (:incident-1 datamap)
                                        :target_entity (:incident-3 datamap)
                                        :timestamp #inst "2042-01-01T00:00:00.000Z"}
                                       {:relationship_type "variant-of"
                                        :target_ref external-ref
                                        :source_ref incident-1-id
                                        :source_entity (:incident-1 datamap)
                                        :target_entity nil
                                        :timestamp #inst "2042-01-01T00:00:00.000Z"}]))

             (testing "sorting"
               (gh/connection-sort-test
                app
                "IncidentQueryTest"
                graphql-queries
                {:id incident-1-id}
                [:incident :relationships]
                relationship-fields)))

           (testing "feedbacks connection"
             (gh/connection-test app
                                 "IncidentFeedbacksQueryTest"
                                 graphql-queries
                                 {:id incident-1-id}
                                 [:incident :feedbacks]
                                 [(dissoc (:feedback-1 datamap) :id :tlp :type :schema_version)
                                  (dissoc (:feedback-2 datamap) :id :tlp :type :schema_version)])

             (testing "sorting"
               (gh/connection-sort-test
                app
                "IncidentFeedbacksQueryTest"
                graphql-queries
                {:id incident-1-id}
                [:incident :feedbacks]
                feedback-fields))))
         (testing "incidents query"
           (testing "incidents connection"
             (gh/connection-test app
                                 "IncidentsQueryTest"
                                 graphql-queries
                                 {"query" "*"}
                                 [:incidents]
                                 [(:incident-1 datamap)
                                  (:incident-2 datamap)
                                  (:incident-3 datamap)])

             (testing "sorting"
               (gh/connection-sort-test
                app
                "IncidentsQueryTest"
                graphql-queries
                {:query "*"}
                [:incidents]
                incident-fields)))

           (testing "query argument"
             (let [{:keys [data errors status]}
                   (gh/query app
                             graphql-queries
                             {:query (format "title:\"%s\""
                                             (get-in
                                              datamap
                                              [:incident-1 :title]))}
                             "IncidentsQueryTest")]
               (is (= 200 status))
               (is (empty? errors) "No errors")
               (is (= 1 (get-in data [:incidents :totalCount]))
                   "Only one incident pattern matches to the query")
               (is (= (:incident-1 datamap)
                      (first (get-in data [:incidents :nodes])))
                   "The incident matches the search query")))))))))
