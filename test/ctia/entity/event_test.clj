(ns ctia.entity.event-test
  (:require
   [cemerick.uri :as uri]
   [clj-momo.lib.clj-time.core :as t]
   [clj-momo.lib.time :as time]
   [clojure.test :refer [is testing deftest use-fixtures]]
   [ctia.auth.capabilities :refer [all-capabilities]]
   [ctia.entity.event :as ev]
   [ctia.test-helpers.core :as helpers
    :refer [DELETE POST PUT GET fixture-with-fixed-time with-sequential-uuid]]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
   [ctim.domain.id :as id]
   [ctim.examples.casebooks :refer [new-casebook-minimal]]
   [ctim.examples.incidents :refer [new-incident-minimal]]
   [ctim.schemas.common :refer [ctim-schema-version]]
   [puppetlabs.trapperkeeper.app :as app]
   [schema.test :refer [validate-schemas]]))

(use-fixtures :each
  validate-schemas
  whoami-helpers/fixture-server)

(deftest test-event-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app
                                "user1"
                                ["group1"]
                                "user1"
                                (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "user1"
                                         "user1"
                                         "group1"
                                         "user1")

     (helpers/set-capabilities! app
                                "user2"
                                ["group1"]
                                "user2"
                                (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "user2"
                                         "user2"
                                         "group1"
                                         "user2")

     (helpers/set-capabilities! app
                                "user3"
                                ["group2"]
                                "user3"
                                (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "user3"
                                         "user3"
                                         "group2"
                                         "user3")

     (testing "simulate Incident activity"
       (with-sequential-uuid
         (fn []
           (fixture-with-fixed-time
            (time/timestamp "2042-01-01")
            (fn []
              (let [app (helpers/get-current-app)
                    {{:keys [get-port]} :CTIAHTTPServerService} (app/service-graph app)

                    port (get-port)
                    {incident :parsed-body
                     incident-status :status}
                    (POST app
                          (str "ctia/incident")
                          :body (assoc new-incident-minimal
                                       :tlp "amber"
                                       :description "my description"
                                       :incident_time
                                       {:opened (time/timestamp "2042-01-01")})
                          :headers {"Authorization" "user1"})

                    {incident-user-3 :parsed-body
                     incident-user-3-status :status}
                    (POST app
                          (str "ctia/incident")
                          :body (assoc new-incident-minimal
                                       :tlp "amber"
                                       :description "my description")
                          :headers {"Authorization" "user3"})
                    {updated-incident :parsed-body
                     updated-incident-status :status}
                    (fixture-with-fixed-time
                     (time/timestamp "2042-01-02")
                     (fn []
                       (PUT app
                            (format "ctia/%s/%s"
                                    "incident"
                                    (-> (:id incident)
                                        id/long-id->id
                                        :short-id))
                            :body (assoc incident
                                         :description "changed description")
                            :headers {"Authorization" "user2"})))

                    {casebook :parsed-body
                     casebook-status :status}
                    (POST app
                          (str "ctia/casebook")
                          :body (assoc new-casebook-minimal
                                       :tlp "amber")
                          :headers {"Authorization" "user1"})

                    {incident-casebook-link :parsed-body
                     incident-casebook-link-status :status}
                    (POST app
                          (format "ctia/%s/%s/link"
                                  "incident"
                                  (-> (:id incident)
                                      id/long-id->id
                                      :short-id))
                          :body {:casebook_id (:id casebook)}
                          :headers {"Authorization" "user1"})
                    {incident-delete-body :parsed-body
                     incident-delete-status :status}
                    (DELETE app
                            (format "ctia/%s/%s"
                                    "incident"
                                    (-> (:id incident)
                                        id/long-id->id
                                        :short-id))
                            :headers {"Authorization" "user1"})
                    uri-timeline-incident-user1
                    (->> (:id incident)
                         uri/uri-encode
                         (str "ctia/event/history/"))
                    uri-timeline-incident-user3
                    (->> (:id incident-user-3)
                         uri/uri-encode
                         (str "ctia/event/history/"))
                    {timeline1-body :parsed-body
                     timeline1-status :status}
                    (GET app
                         uri-timeline-incident-user1
                         :headers {"Authorization" "user1"})
                    {timeline2-body :parsed-body
                     timeline2-status :status}
                    (GET app
                         uri-timeline-incident-user1
                         :headers {"Authorization" "user2"})
                    {timeline3-body :parsed-body
                     timeline3-status :status}
                    (GET app
                         uri-timeline-incident-user1
                         :headers {"Authorization" "user3"})
                    {timeline4-body :parsed-body
                     timeline4-status :status}
                    (GET app
                         uri-timeline-incident-user3
                         :headers {"Authorization" "user1"})
                    {timeline5-body :parsed-body
                     timeline5-status :status}
                    (GET app
                         uri-timeline-incident-user3
                         :headers {"Authorization" "user3"})]

                (is (= 201 incident-status))
                (is (= 201 incident-user-3-status))
                (is (= 200 updated-incident-status))
                (is (= 201 casebook-status))
                (is (= 201 incident-casebook-link-status))
                (is (= 204 incident-delete-status))
                (is (= 200 timeline1-status))
                (is (= 200 timeline2-status))
                (is (= 200 timeline3-status))
                (is (= 200 timeline4-status))
                (is (= 200 timeline5-status))

                (testing "event timeline should contain all actions by user, with respect to their visibility"

                  (is (= '(1 3) (map :count timeline1-body)))
                  (is (= #{"user1" "user2"}
                         (set (map :owner timeline1-body)))
                      "owners should differ")
                  (is (empty? timeline3-body))
                  (is (empty? timeline4-body))
                  (is (every? #(= "user3" (:owner %))
                              timeline5-body))
                  (is (= '(1) (map :count timeline5-body))))

                (testing "should be able to list all related incident events filtered with Access control"
                  (let [q (uri/uri-encode
                           (format "entity.id:\"%s\" OR entity.source_ref:\"%s\" OR entity.target_ref:\"%s\""
                                   (:id incident)
                                   (:id incident)
                                   (:id incident)))
                        results (:parsed-body (GET app
                                                   (str "ctia/event/search?query=" q)
                                                   :content-type :json
                                                   :headers {"Authorization" "user1"}))
                        event-id (-> results
                                     first
                                     :id
                                     id/long-id->id
                                     :short-id)]
                    (testing "should be able to GET an event"
                      (is (= 200 (:status (GET app
                                               (str "ctia/event/" event-id)
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
                              :timestamp "2042-01-01T00:00:00.000Z",
                              :incident_time {:opened "2042-01-01T00:00:00.000Z"},
                              :status "Open",
                              :id
                              (format "http://localhost:%s/ctia/incident/incident-00000000-0000-0000-0000-111111111112"
                                      port),
                              :tlp "amber",
                              :groups ["group1"],
                              :confidence "High",
                              :owner "user1"},
                             :id
                             (format "http://localhost:%s/ctia/event/event-00000000-0000-0000-0000-111111111113"
                                     port),
                             :type "event",
                             :event_type :record-created}
                            {:owner "user2",
                             :groups ["group1"],
                             :timestamp #inst "2042-01-02T00:00:00.000-00:00",
                             :tlp "amber"
                             :entity
                             {:description "changed description",
                              :schema_version ctim-schema-version,
                              :type "incident",
                              :created "2042-01-01T00:00:00.000Z",
                              :modified "2042-01-02T00:00:00.000Z",
                              :timestamp "2042-01-01T00:00:00.000Z",
                              :incident_time {:opened "2042-01-01T00:00:00.000Z"},
                              :status "Open",
                              :id
                              (format "http://localhost:%s/ctia/incident/incident-00000000-0000-0000-0000-111111111112"
                                      port),
                              :tlp "amber",
                              :groups ["group1"],
                              :confidence "High",
                              :owner "user2"},
                             :id
                             (format "http://localhost:%s/ctia/event/event-00000000-0000-0000-0000-111111111116"
                                     port),
                             :type "event",
                             :event_type :record-updated,
                             :fields
                             [{:field :description,
                               :action "modified",
                               :change
                               {:before "my description",
                                :after "changed description"}}
                              {:field :modified,
                               :action "modified",
                               :change
                               {:before "2042-01-01T00:00:00.000Z",
                                :after "2042-01-02T00:00:00.000Z"}}
                              {:field  :owner,
                               :action "modified",
                               :change {:before "user1", :after "user2"}}]}
                            {:owner "user1",
                             :groups ["group1"],
                             :timestamp #inst "2042-01-01T00:00:00.000-00:00",
                             :tlp "amber"
                             :entity
                             {:schema_version ctim-schema-version,
                              :target_ref
                              (format "http://localhost:%s/ctia/incident/incident-00000000-0000-0000-0000-111111111112"
                                      port),
                              :type "relationship",
                              :created "2042-01-01T00:00:00.000Z",
                              :modified "2042-01-01T00:00:00.000Z",
                              :timestamp "2042-01-01T00:00:00.000Z",
                              :source_ref
                              (format "http://localhost:%s/ctia/casebook/casebook-00000000-0000-0000-0000-111111111117"
                                      port),
                              :id
                              (format "http://localhost:%s/ctia/relationship/relationship-00000000-0000-0000-0000-111111111119"
                                      port),
                              :tlp "amber",
                              :groups ["group1"],
                              :owner "user1",
                              :relationship_type "related-to"},
                             :id
                             (format "http://localhost:%s/ctia/event/event-00000000-0000-0000-0000-111111111120"
                                     port),
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
                              :timestamp "2042-01-01T00:00:00.000Z",
                              :incident_time {:opened "2042-01-01T00:00:00.000Z"},
                              :status "Open",
                              :id
                              (format "http://localhost:%s/ctia/incident/incident-00000000-0000-0000-0000-111111111112"
                                      port),
                              :tlp "amber",
                              :groups ["group1"],
                              :confidence "High",
                              :owner "user1"},
                             :id
                             (format "http://localhost:%s/ctia/event/event-00000000-0000-0000-0000-111111111121"
                                     port),
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
  (let [get-in-config (helpers/build-get-in-config-fn)
        t1 (t/internal-now)
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
      (is (true? (ev/same-bucket? bucket1 event3 get-in-config)))
      (is (true? (ev/same-bucket? bucket2 event3 get-in-config)))
      (is (false? (ev/same-bucket? bucket1 event4 get-in-config)))
      (is (false? (ev/same-bucket? bucket2 event4 get-in-config)))
      (is (false? (ev/same-bucket? bucket2 event5 get-in-config))))))

(deftest bucketize-events-test
  (let [get-in-config (helpers/build-get-in-config-fn)
        now (t/internal-now)
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
        timeline (ev/bucketize-events events get-in-config)]
    (testing "bucketize function should group events in near same time from same owner"
      (is (< (count timeline) (count events)))
      (is (= (+ (count every-min) (count every-day) 3)
             (count timeline)))
      (is (= (count every-sec) (count (-> timeline first :events))))
      (is (= (count every-milli-2) (count (-> timeline second :events)))))))

(deftest test-event-diffs
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app
                                "user1"
                                ["group1"]
                                "user1"
                                (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "user1"
                                         "user1"
                                         "group1"
                                         "user1")
     ;; test for https://github.com/threatgrid/iroh/issues/3551
     (testing "Diff events for Incidents"
       (with-sequential-uuid
         (fn []
           (fixture-with-fixed-time
            (time/timestamp "2042-01-01")
            (fn []
              (let [initial-incident
                    (POST app
                          (str "ctia/incident")
                          :body new-incident-minimal
                          :headers {"Authorization" "user1"})
                    _ (is (= 201 (:status initial-incident)) initial-incident)

                    ;; add new :assignees field
                    added-assignees-incident
                    (PUT app
                         (format "ctia/%s/%s"
                                 "incident"
                                 (-> (get-in initial-incident [:parsed-body :id])
                                     id/long-id->id
                                     :short-id))
                         :body (-> initial-incident
                                   :parsed-body
                                   (assoc :assignees ["1"]))
                         :headers {"Authorization" "user1"})
                    _ (is (= 200 (:status added-assignees-incident))
                          added-assignees-incident)

                    ;; update existing :assignees field
                    modified-assignees-incident
                    (PUT app
                         (format "ctia/%s/%s"
                                 "incident"
                                 (-> (get-in added-assignees-incident [:parsed-body :id])
                                     id/long-id->id
                                     :short-id))
                         :body (-> added-assignees-incident
                                   :parsed-body
                                   (assoc-in [:assignees 1] "2"))
                         :headers {"Authorization" "user1"})
                    _ (is (= 200 (:status modified-assignees-incident))
                          modified-assignees-incident)

                    ;; delete existing :assignees field
                    deleted-assignees-incident
                    (PUT app
                         (format "ctia/%s/%s"
                                 "incident"
                                 (-> (get-in modified-assignees-incident [:parsed-body :id])
                                     id/long-id->id
                                     :short-id))
                         :body (-> modified-assignees-incident
                                   :parsed-body
                                   (dissoc :assignees))
                         :headers {"Authorization" "user1"})
                    _ (is (= 200 (:status deleted-assignees-incident))
                          deleted-assignees-incident)]

                (testing ":fields are correctly set"
                  (let [initial-id (get-in initial-incident [:parsed-body :id])
                        q (uri/uri-encode
                            (format "entity.id:\"%s\"" initial-id))
                        results (map :fields
                                     (:parsed-body (GET app
                                                        (str "ctia/event/search?sort_by=timestamp&query=" q)
                                                        :content-type :json
                                                        :headers {"Authorization" "user1"})))]
                    (is (= [nil
                            [{:field :assignees
                              :action "added"
                              :change {:after ["1"]}}]
                            [{:field :assignees
                              :action "modified"
                              :change {:before ["1"], :after ["1" "2"]}}]
                            [{:field :assignees
                              :action "deleted"
                              :change {:before ["1" "2"]}}]]
                           results)))))))))))))
