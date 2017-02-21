(ns ctia.http.routes.relationship-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers
             [core :as mth]
             [http :refer [encode]]]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctia.test-helpers
             [search :refer [test-query-string-search]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-relationship-routes-bad-reference
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /cita/relationship"
    (let [{status :status
           {error :error} :parsed-body}
          (post "ctia/relationship"
                :body {:external_ids ["http://ex.tld/ctia/relationship/relationship-123"
                                      "http://ex.tld/ctia/relationship/relationship-456"]
                       :type "relationship"
                       :title "title"
                       :description "description"
                       :short_description "short desc"
                       :revision 1
                       :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                       :language "language"
                       :tlp "green"
                       :source "source"
                       :source_uri "http://example.com"
                       :relationship_type "anything"
                       :source_ref "http://example.com/"
                       :target_ref "http://example.com/"}
                :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 400 status)))))

(deftest-for-each-store test-relationship-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/relationship"
    (let [{status :status
           relationship :parsed-body}
          (post "ctia/relationship"
                :body
                {:external_ids ["http://ex.tld/ctia/relationship/relationship-123"
                                "http://ex.tld/ctia/relationship/relationship-456"]
                 :type "relationship"
                 :title "title"
                 :description "description"
                 :short_description "short desc"
                 :revision 1
                 :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                 :language "language"
                 :tlp "green"
                 :source "source"
                 :source_uri "http://example.com"
                 :relationship_type "based-on"
                 :source_ref (str "http://example.com/ctia/judgement/judgement-"
                                  "f9832ac2-ee90-4e18-9ce6-0c4e4ff61a7a")
                 :target_ref (str "http://example.com/ctia/indicator/indicator-"
                                  "8c94ca8d-fb2b-4556-8517-8e6923d8d3c7")}
                :headers {"api_key" "45c1f5e3f05d0"})
          relationship-id (id/long-id->id (:id relationship))
          relationship-external-ids
          (:external_ids relationship)]
      (is (= 201 status))
      (is (deep=
           {:id (id/long-id relationship-id)
            :external_ids ["http://ex.tld/ctia/relationship/relationship-123"
                           "http://ex.tld/ctia/relationship/relationship-456"]
            :type "relationship"
            :title "title"
            :description "description"
            :short_description "short desc"
            :revision 1
            :timestamp #inst "2016-02-11T00:40:48.212-00:00"
            :schema_version schema-version
            :language "language"
            :tlp "green"
            :source "source"
            :source_uri "http://example.com"
            :relationship_type "based-on"
            :source_ref (str "http://example.com/ctia/judgement/judgement-"
                             "f9832ac2-ee90-4e18-9ce6-0c4e4ff61a7a")
            :target_ref (str "http://example.com/ctia/indicator/indicator-"
                             "8c94ca8d-fb2b-4556-8517-8e6923d8d3c7")}
           relationship))

      (testing "the relationship ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    relationship-id)      (:hostname    show-props)))
          (is (= (:protocol    relationship-id)      (:protocol    show-props)))
          (is (= (:port        relationship-id)      (:port        show-props)))
          (is (= (:path-prefix relationship-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/relationship/:id"
        (let [response (get (str "ctia/relationship/" (:short-id relationship-id))
                            :headers {"api_key" "45c1f5e3f05d0"})
              relationship (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (:id relationship)
                :external_ids ["http://ex.tld/ctia/relationship/relationship-123"
                               "http://ex.tld/ctia/relationship/relationship-456"]
                :type "relationship"
                :title "title"
                :description "description"
                :short_description "short desc"
                :revision 1
                :schema_version schema-version
                :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                :language "language"
                :tlp "green"
                :source "source"
                :source_uri "http://example.com"
                :relationship_type "based-on"
                :source_ref (str "http://example.com/ctia/judgement/judgement-"
                                 "f9832ac2-ee90-4e18-9ce6-0c4e4ff61a7a")
                :target_ref (str "http://example.com/ctia/indicator/indicator-"
                                 "8c94ca8d-fb2b-4556-8517-8e6923d8d3c7")}
               relationship))))

      (test-query-string-search :relationship "description" :description)

      (testing "GET /ctia/relationship/external_id/:external_id"
        (let [response (get (format "ctia/relationship/external_id/%s"
                                    (encode (rand-nth relationship-external-ids)))
                            :headers {"api_key" "45c1f5e3f05d0"})
              relationships (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (:id relationship)
                 :external_ids ["http://ex.tld/ctia/relationship/relationship-123"
                                "http://ex.tld/ctia/relationship/relationship-456"]
                 :type "relationship"
                 :title "title"
                 :description "description"
                 :short_description "short desc"
                 :revision 1
                 :schema_version schema-version
                 :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                 :language "language"
                 :tlp "green"
                 :source "source"
                 :source_uri "http://example.com"
                 :relationship_type "based-on"
                 :source_ref (str "http://example.com/ctia/judgement/judgement-"
                                  "f9832ac2-ee90-4e18-9ce6-0c4e4ff61a7a")
                 :target_ref (str "http://example.com/ctia/indicator/indicator-"
                                  "8c94ca8d-fb2b-4556-8517-8e6923d8d3c7")}]
               relationships))))

      (testing "DELETE /ctia/relationship/:id"
        (let [response (delete (str "ctia/relationship/" (:short-id relationship-id))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/relationship/" (:short-id relationship-id))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
