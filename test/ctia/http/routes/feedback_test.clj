(ns ctia.http.routes.feedback-test
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [properties]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
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
                       :type "feedback"
                       :reason "false positive"
                       :tlp "green"}
                :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 201 status))
      (is (deep=
           {:feedback -1,
            :entity_id "judgement-123"
            :type "feedback"
            :reason "false positive"
            :schema_version schema-version
            :tlp "green"}
           (dissoc feedback :id :created :owner)))

      (testing "GET /ctia/feedback/:id"
        (let [response (get (str "ctia/feedback/" (:id feedback))
                            :headers {"api_key" "45c1f5e3f05d0"})
              feedback (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:feedback -1,
                :entity_id "judgement-123"
                :reason "false positive"
                :type "feedback"
                :schema_version schema-version
                :tlp "green"}
               (dissoc feedback :id :created :owner)))))

      (testing "GET /ctia/feedback?entity_id="
        (let [response (get (str "ctia/feedback")
                            :query-params {:entity_id "judgement-123"}
                            :headers {"api_key" "45c1f5e3f05d0"})
              feedbacks (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:feedback -1,
                 :entity_id "judgement-123"
                 :type "feedback"
                 :reason "false positive"
                 :schema_version schema-version
                 :tlp "green"}]
               (map #(dissoc % :id :created :owner) feedbacks)))))

      (testing "DELETE /ctia/feedback/:id"
        (let [temp-feedback (-> (post "ctia/feedback"
                                      :body {:feedback -1,
                                             :entity_id "judgement-42"
                                             :reason "false positive"
                                             :tlp "green"}
                                      :headers {"api_key" "45c1f5e3f05d0"})
                                :parsed-body)
              response (delete (str "ctia/feedback/" (:id temp-feedback))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/feedback/" (:id temp-feedback))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
