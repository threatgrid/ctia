(ns ctia.entity.event-test
  (:refer-clojure :exclude [get])
  (:require
   [ctim.domain.id :refer [str->short-id]]
   [clojure
    [string :as str]
    [test :refer [is testing]]]
   [clj-momo.test-helpers.core :as mth]
   [clojure.test :refer [deftest join-fixtures use-fixtures]]
   [ctim.domain.id :as id]
   [ctia.test-helpers
    [auth :refer [all-capabilities]]
    [core :as helpers :refer [delete post put get]]
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
       (let [{incident :parsed-body
              incident-status :status}
             (post (str "ctia/incident")
                   :body (assoc new-incident-minimal
                                :tlp "amber"
                                :description "my description")
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
             (put (format "ctia/%s/%s"
                          "incident"
                          (-> (:id incident)
                              id/long-id->id
                              :short-id))
                  :body (assoc incident
                               :description "changed description")
                  :headers {"Authorization" "user2"})

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


         (clojure.pprint/pprint
          (let [q (url-encode
                   (format "entity.id:\"%s\" OR entity.source_ref:\"%s\" OR entity.target_ref:\"%s\""
                           (:id incident)
                           (:id incident)
                           (:id incident)))]
            (:parsed-body (get (str "ctia/event/search?query=" q)
                               :accept :json
                               :headers {"Authorization" "user1"})))))))))
