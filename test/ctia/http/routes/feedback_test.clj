(ns ctia.http.routes.feedback-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clj-momo.test-helpers.http :refer [encode]]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-feedback-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (helpers/set-capabilities! "baruser" "user" #{})
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")
  (whoami-helpers/set-whoami-response "2222222222222" "baruser" "user")

  (testing "POST /ctia/feedback"
    (let [{feedback :parsed-body
           status :status}
          (post "ctia/feedback"
                :body {:feedback -1,
                       :entity_id "judgement-123"
                       :external_ids ["http://ex.tld/ctia/feedback/feedback-123"
                                      "http://ex.tld/ctia/feedback/feedback-456"]
                       :type "feedback"
                       :reason "false positive"
                       :tlp "green"}
                :headers {"api_key" "45c1f5e3f05d0"})

          feedback-id (id/long-id->id (:id feedback))
          feedback-external-ids (:external_ids feedback)]
      (is (= 201 status))
      (is (deep=
           {:id (id/long-id feedback-id)
            :feedback -1,
            :entity_id "judgement-123"
            :external_ids ["http://ex.tld/ctia/feedback/feedback-123"
                           "http://ex.tld/ctia/feedback/feedback-456"]
            :type "feedback"
            :reason "false positive"
            :schema_version schema-version
            :tlp "green"}
           feedback))

      (testing "the feedback ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    feedback-id)      (:hostname    show-props)))
          (is (= (:protocol    feedback-id)      (:protocol    show-props)))
          (is (= (:port        feedback-id)      (:port        show-props)))
          (is (= (:path-prefix feedback-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/feedback/:id"
        (let [response (get (str "ctia/feedback/" (:short-id feedback-id))
                            :headers {"api_key" "45c1f5e3f05d0"})
              feedback (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id feedback-id)
                :feedback -1,
                :entity_id "judgement-123"
                :external_ids ["http://ex.tld/ctia/feedback/feedback-123"
                               "http://ex.tld/ctia/feedback/feedback-456"]
                :reason "false positive"
                :type "feedback"
                :schema_version schema-version
                :tlp "green"}
               feedback))))

      (testing "GET /ctia/feedback?entity_id="
        (let [response (get (str "ctia/feedback")
                            :query-params {:entity_id "judgement-123"}
                            :headers {"api_key" "45c1f5e3f05d0"})
              feedbacks (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id feedback-id)
                 :feedback -1,
                 :entity_id "judgement-123"
                 :external_ids ["http://ex.tld/ctia/feedback/feedback-123"
                                "http://ex.tld/ctia/feedback/feedback-456"]
                 :type "feedback"
                 :reason "false positive"
                 :schema_version schema-version
                 :tlp "green"}]
               feedbacks))))

      (testing "GET /ctia/feedback/external_id/:external_id"
        (let [response (get (format "ctia/feedback/external_id/%s"
                                    (encode (rand-nth feedback-external-ids)))
                            :headers {"api_key" "45c1f5e3f05d0"})
              feedback (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id feedback-id)
                 :feedback -1,
                 :entity_id "judgement-123"
                 :external_ids ["http://ex.tld/ctia/feedback/feedback-123"
                                "http://ex.tld/ctia/feedback/feedback-456"]
                 :type "feedback"
                 :reason "false positive"
                 :schema_version schema-version
                 :tlp "green"}]
               feedback))))

      (testing "DELETE /ctia/feedback/:id"
        (let [temp-feedback (-> (post "ctia/feedback"
                                      :body {:feedback -1,
                                             :entity_id "judgement-42"
                                             :reason "false positive"
                                             :tlp "green"}
                                      :headers {"api_key" "45c1f5e3f05d0"})
                                :parsed-body)

              temp-feedback-id (id/long-id->id (:id temp-feedback))
              response (delete (str "ctia/feedback/" (:short-id temp-feedback-id))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/feedback/" (:short-id temp-feedback-id))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
