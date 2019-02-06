(ns ctia.entity.event-test
  (:refer-clojure :exclude [get])
  (:require
   [ctim.schemas.common :refer [ctim-schema-version]]
   [ctim.domain.id :refer [str->short-id]]
   [clojure
    [string :as str]
    [test :refer [is testing]]]
   [clj-momo.lib.time :as time]
   [clj-momo.test-helpers.core :as mth]
   [clj-momo.lib.clj-time.core :as t]
   [clojure.test :refer [deftest join-fixtures use-fixtures]]
   [ctim.domain.id :as id]
   [ctia.entity.event :as ev]
   [ctia.test-helpers
    [auth :refer [all-capabilities]]
    [core :as helpers
     :refer [delete post put get fixture-with-fixed-time
             with-sequential-uuid]]
    [fake-whoami-service :as whoami-helpers]
    [store :refer [test-for-each-store]]]
   [ctim.examples.incidents :refer [new-incident-minimal]]
   [ctim.examples.casebooks :refer [new-casebook-minimal]]
   [ctim.domain.id :as id]
   [cemerick.url :refer [url-encode]]))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest test-event-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "user1"
                                ["group1"]
                                "user1"
                                all-capabilities)
     (whoami-helpers/set-whoami-response "user1"
                                         "user1"
                                         "group1"
                                         "user1")

     (helpers/set-capabilities! "user2"
                                ["group1"]
                                "user2"
                                all-capabilities)
     (whoami-helpers/set-whoami-response "user2"
                                         "user2"
                                         "group1"
                                         "user2")

     (helpers/set-capabilities! "user3"
                                ["group2"]
                                "user3"
                                all-capabilities)
     (whoami-helpers/set-whoami-response "user3"
                                         "user3"
                                         "group2"
                                         "user3")

     (testing "simulate Incident activity"
       (with-sequential-uuid
         (fn []
           (fixture-with-fixed-time
            (time/timestamp "2042-01-01")
            (fn []
              (let [{incident :parsed-body
                     incident-status :status}
                    (post (str "ctia/incident")
                          :body (assoc new-incident-minimal
                                       :tlp "amber"
                                       :description "my description"
                                       :incident_time
                                       {:opened (time/timestamp "2042-01-01")})
                          :headers {"Authorization" "user1"})

                    {incident-user-3 :parsed-body
                     incident-user-3-status :status}
                    (post (str "ctia/incident")
                          :body (assoc new-incident-minimal
                                       :tlp "amber"
                                       :description "my description")
                          :headers {"Authorization" "user3"})
                    {updated-incident :parsed-body
                     updated-incident-status :status}
                    (fixture-with-fixed-time
                     (time/timestamp "2042-01-02")
                     (fn []
                       (put (format "ctia/%s/%s"
                                    "incident"
                                    (-> (:id incident)
                                        id/long-id->id
                                        :short-id))
                            :body (assoc incident
                                         :description "changed description")
                            :headers {"Authorization" "user2"})))

                    {casebook :parsed-body
                     casebook-status :status}
                    (post (str "ctia/casebook")
                          :body (assoc new-casebook-minimal
                                       :tlp "amber")
                          :headers {"Authorization" "user1"})

                    {incident-casebook-link :parsed-body
                     incident-casebook-link-status :status}
                    (post (format "ctia/%s/%s/link"
                                  "incident"
                                  (-> (:id incident)
                                      id/long-id->id
                                      :short-id))
                          :body {:casebook_id (:id casebook)}
                          :headers {"Authorization" "user1"})
                    {incident-delete-body :parsed-body
                     incident-delete-status :status}
                    (delete (format "ctia/%s/%s"
                                    "incident"
                                    (-> (:id incident)
                                        id/long-id->id
                                        :short-id))
                            :headers {"Authorization" "user1"})]

                (is (= 201 incident-status))
                (is (= 201 incident-user-3-status))
                (is (= 200 updated-incident-status))
                (is (= 201 casebook-status))
                (is (= 201 incident-casebook-link-status))
                (is (= 204 incident-delete-status))

                (testing "should be able to list all related incident events filtered with Access control"
                  (let [q (url-encode
                           (format "entity.id:\"%s\" OR entity.source_ref:\"%s\" OR entity.target_ref:\"%s\""
                                   (:id incident)
                                   (:id incident)
                                   (:id incident)))
                        results (:parsed-body (get (str "ctia/event/search?query=" q)
                                                   :content-type :json
                                                   :headers {"Authorization" "user1"}))
                        event-id (-> results
                                     first
                                     :id
                                     id/long-id->id
                                     :short-id)]
                    (testing "should be able to GET an event"
                      (is (= 200 (:status (get (str "ctia/event/" event-id)
                                               :headers {"Authorization" "user1"})))))

                    (is (= [{:owner "user1",
                             :groups ["group1"],
                             :timestamp #inst "2042-01-01T00:00:00.000-00:00",
                             :tlp "amber"
                             :entity
                             {:description "my description",
                              :schema_version ctim-schema-version,
                              :type "incident",
                              :created "2042-01-01T00:00:00.000Z",
                              :modified "2042-01-01T00:00:00.000Z",
                              :incident_time {:opened "2042-01-01T00:00:00.000Z"},
                              :status "Open",
                              :id
                              "http://localhost:3001/ctia/incident/incident-00000000-0000-0000-0000-111111111112",
                              :tlp "amber",
                              :groups ["group1"],
                              :confidence "High",
                              :owner "user1"},
                             :id
                             "http://localhost:3001/ctia/event/event-00000000-0000-0000-0000-111111111113",
                             :type "event",
                             :event_type :record-created}
                            {:owner "user1",
                             :groups ["group1"],
                             :timestamp #inst "2042-01-02T00:00:00.000-00:00",
                             :tlp "amber"
                             :entity
                             {:description "changed description",
                              :schema_version ctim-schema-version,
                              :type "incident",
                              :created "2042-01-01T00:00:00.000Z",
                              :modified "2042-01-02T00:00:00.000Z",
                              :incident_time {:opened "2042-01-01T00:00:00.000Z"},
                              :status "Open",
                              :id
                              "http://localhost:3001/ctia/incident/incident-00000000-0000-0000-0000-111111111112",
                              :tlp "amber",
                              :groups ["group1"],
                              :confidence "High",
                              :owner "user1"},
                             :id
                             "http://localhost:3001/ctia/event/event-00000000-0000-0000-0000-111111111116",
                             :type "event",
                             :event_type :record-updated,
                             :fields
                             [{:field :modified,
                               :action "modifed",
                               :change
                               {:before "2042-01-01T00:00:00.000Z",
                                :after "2042-01-02T00:00:00.000Z"}}
                              {:field :description,
                               :action "modifed",
                               :change
                               {:before "my description",
                                :after "changed description"}}]}
                            {:owner "user1",
                             :groups ["group1"],
                             :timestamp #inst "2042-01-01T00:00:00.000-00:00",
                             :tlp "amber"
                             :entity
                             {:schema_version ctim-schema-version,
                              :target_ref
                              "http://localhost:3001/ctia/incident/incident-00000000-0000-0000-0000-111111111112",
                              :type "relationship",
                              :created "2042-01-01T00:00:00.000Z",
                              :modified "2042-01-01T00:00:00.000Z",
                              :source_ref
                              "http://localhost:3001/ctia/casebook/casebook-00000000-0000-0000-0000-111111111117",
                              :id
                              "http://localhost:3001/ctia/relationship/relationship-00000000-0000-0000-0000-111111111119",
                              :tlp "amber",
                              :groups ["group1"],
                              :owner "user1",
                              :relationship_type "related-to"},
                             :id
                             "http://localhost:3001/ctia/event/event-00000000-0000-0000-0000-111111111120",
                             :type "event",
                             :event_type :record-created}
                            {:owner "user1",
                             :groups ["group1"],
                             :timestamp #inst "2042-01-01T00:00:00.000-00:00",
                             :tlp "amber"
                             :entity
                             {:description "changed description",
                              :schema_version ctim-schema-version,
                              :type "incident",
                              :created "2042-01-01T00:00:00.000Z",
                              :modified "2042-01-02T00:00:00.000Z",
                              :incident_time {:opened "2042-01-01T00:00:00.000Z"},
                              :status "Open",
                              :id
                              "http://localhost:3001/ctia/incident/incident-00000000-0000-0000-0000-111111111112",
                              :tlp "amber",
                              :groups ["group1"],
                              :confidence "High",
                              :owner "user1"},
                             :id
                             "http://localhost:3001/ctia/event/event-00000000-0000-0000-0000-111111111121",
                             :type "event",
                             :event_type :record-deleted}]
                           results)))))))))))))

(defn get-event [owner event_type timestamp]
  {:owner owner
   :event_type event_type
   :timestamp timestamp
   :type "event"
   :groups ["a group"]
   :tlp "green"
   :entity {}
   :id "an id"})

(defn generate-events [owner event_type from n fn-unit]
  (->> (map #(t/plus from (fn-unit %)) (range n))
       (map (partial get-event owner event_type))))


(deftest bucket-operations-test
  (let [t1 (t/internal-now)
        t2 (t/plus t1 (t/seconds 1))
        t3 (t/plus t1 (t/seconds 2))
        t4 (t/plus t1 (t/days 1))
        event1 (get-event "Smith" :record-created t1)
        event2 (get-event "Smith" :record-updated t2)
        event3 (get-event "Smith" :record-updated t3)
        event4 (get-event "Doe" :record-updated t4)
        event5 (get-event "Doe" :record-updated t1)
        bucket1 (ev/init-bucket event1)
        bucket2 (ev/bucket-append bucket1 event2)]
    (testing "init-bucket should properly initialize a bucket from an event"
      (is (= (:from bucket1) (:to bucket1) (:timestamp event1)))
      (is (= 1 (:count bucket1))))
    (testing "bucket-append should properly append an event to a bucket"
      (is (= (:from bucket2) (:timestamp event1)))
      (is (= (:to bucket2) (:timestamp event2)))
      (is (= #{event1 event2} (set (:events bucket2))))
      (is (= 2 (:count bucket2))))
    (testing "same-bucket? shoud properly assert that an event is part of a bucket or not"
      (is (true? (ev/same-bucket? bucket1 event3)))
      (is (true? (ev/same-bucket? bucket2 event3)))
      (is (false? (ev/same-bucket? bucket1 event4)))
      (is (false? (ev/same-bucket? bucket2 event4)))
      (is (false? (ev/same-bucket? bucket2 event5))))))

(deftest bucketize-events-test
  (let [now (t/internal-now)
        one-hour-ago (t/minus now (t/hours 1))
        two-hours-ago (t/minus now (t/hours 2))
        one-month-ago (t/minus now (t/months 1))

        every-sec (concat (generate-events "Doe" :record-created now 1 t/seconds)
                          (generate-events "Doe" :record-updated now 2 t/seconds))
        every-milli-1 (generate-events "Smith" :record-updated one-hour-ago 10 t/millis)
        every-milli-2 (generate-events "Doe" :record-updated one-hour-ago 20 t/millis)
        every-min (generate-events "Smith" :record-updated two-hours-ago 4 t/minutes)
        every-day (generate-events "Doe" :record-updated one-month-ago 3 t/days)

        events (->> (concat every-sec every-min every-day every-milli-1 every-milli-2)
                    shuffle)
        timeline (ev/bucketize-events events)]
    (testing "bucketize function should group events in near same time from same owner"
      (is (< (count timeline) (count events)))
      (is (= (+ (count every-min) (count every-day) 3)
             (count timeline)))
      (is (= (count every-sec) (count (-> timeline first :events))))
      (is (= (count every-milli-2) (count (-> timeline second :events)))))))
