(ns ctia.http.routes.actor-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers
             [core :as mth]
             [http :refer [encode]]]
            [clojure
             [string :as str]
             [test :refer [is join-fixtures testing use-fixtures]]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [search :refer [test-query-string-search]]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]
            [ctim.examples.actors :as ex]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-actor-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/actor"
    (let [{status :status
           actor :parsed-body}
          (post "ctia/actor"
                :body {:external_ids ["http://ex.tld/ctia/actor/actor-123"
                                      "http://ex.tld/ctia/actor/actor-456"]
                       :title "actor"
                       :description "description"
                       :actor_type "Hacker"
                       :source "a source"
                       :confidence "High"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                    :end_time "2016-07-11T00:40:48.212-00:00"}}
                :headers {"Authorization" "45c1f5e3f05d0"})

          actor-id
          (id/long-id->id (:id actor))

          actor-external-ids
          (:external_ids actor)]
      (is (= 201 status))
      (is (deep=
           {:external_ids ["http://ex.tld/ctia/actor/actor-123"
                           "http://ex.tld/ctia/actor/actor-456"]
            :type "actor"
            :description "description",
            :actor_type "Hacker",
            :title "actor",
            :confidence "High",
            :source "a source"
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2016-07-11T00:40:48.212-00:00"}
            :schema_version schema-version
            :tlp "green"}
           (dissoc actor
                   :id)))

      (testing "the actor ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    actor-id)      (:hostname    show-props)))
          (is (= (:protocol    actor-id)      (:protocol    show-props)))
          (is (= (:port        actor-id)      (:port        show-props)))
          (is (= (:path-prefix actor-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/actor/:id"
        (let [response (get (str "ctia/actor/" (:short-id actor-id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id actor-id)
                :external_ids ["http://ex.tld/ctia/actor/actor-123"
                               "http://ex.tld/ctia/actor/actor-456"]
                :type "actor"
                :description "description",
                :actor_type "Hacker",
                :title "actor",
                :confidence "High",
                :source "a source"
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :schema_version schema-version
                :tlp "green"}
               actor))))

      (test-query-string-search :actor "description" :description)

      (testing "GET /ctia/actor/external_id/:external_id"
        (let [response (get (format "ctia/actor/external_id/%s"
                                    (encode (rand-nth actor-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              actors (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id actor-id)
                 :external_ids ["http://ex.tld/ctia/actor/actor-123"
                                "http://ex.tld/ctia/actor/actor-456"]
                 :type "actor"
                 :description "description",
                 :actor_type "Hacker",
                 :title "actor",
                 :confidence "High",
                 :source "a source"
                 :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                              :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                 :schema_version schema-version
                 :tlp "green"}]
               actors))))

      (testing "PUT /ctia/actor/:id"
        (let [response (put (str "ctia/actor/" (:short-id actor-id))
                            :body {:external_ids ["http://ex.tld/ctia/actor/actor-123"
                                                  "http://ex.tld/ctia/actor/actor-456"]
                                   :title "modified actor"
                                   :description "updated description"
                                   :actor_type "Hacktivist"
                                   :type "actor"
                                   :source "a source"
                                   :confidence "High"
                                   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                                :end_time "2016-07-11T00:40:48.212-00:00"}}
                            :headers {"Authorization" "45c1f5e3f05d0"})
              updated-actor (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id actor-id)
                :external_ids ["http://ex.tld/ctia/actor/actor-123"
                               "http://ex.tld/ctia/actor/actor-456"]
                :type "actor"
                :title "modified actor"
                :description "updated description"
                :actor_type "Hacktivist"
                :source "a source"
                :confidence "High"
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                :schema_version schema-version
                :tlp "green"}
               updated-actor))))

      (testing "PUT invalid /ctia/actor/:id"
        (let [{status :status
               body :body}
              (put (str "ctia/actor/" (:short-id actor-id))
                   :body {:external_ids ["http://ex.tld/ctia/actor/actor-123"
                                         "http://ex.tld/ctia/actor/actor-456"]
                          ;; This field has an invalid length
                          :title (apply str (repeatedly 1025 (constantly \0)))
                          :description "updated description"
                          :actor_type "Hacktivist"
                          :type "actor"
                          :source "a source"
                          :confidence "High"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= status 400))
          (is (re-find #"error.*in.*title" (str/lower-case body)))))

      (testing "DELETE /ctia/actor/:id"
        (let [response (delete (str "ctia/actor/" (:short-id actor-id))
                               :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/actor/" (:short-id actor-id))
                              :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))))

  (testing "POST invalid /ctia/actor"
    (let [{status :status
           body :body}
          (post "ctia/actor"
                :body (assoc ex/new-actor-minimal
                             ;; This field has an invalid length
                             :title (apply str (repeatedly 1025 (constantly \0))))
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*title" (str/lower-case body))))))
