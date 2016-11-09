(ns ctia.http.routes.relationship-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [search :refer [test-query-string-search]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

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
                 :uri "http://example.com"
                 :revision 1
                 :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                 :language "language"
                 :tlp "green"
                 :source "source"
                 :source_uri "http://example.com"
                 :relationship_type "targets"
                 :source_ref "http://example.com"
                 :target_ref "http://example.com"}
                :headers {"api_key" "45c1f5e3f05d0"})

          relationship-id (id/long-id->id (:id relationship))
          relationship-external-ids
          (:external_ids relationship)]
      (is (= 201 status))
      (is (deep=
           {:external_ids ["http://ex.tld/ctia/relationship/relationship-123"
                           "http://ex.tld/ctia/relationship/relationship-456"]
            :type "relationship"
            :title "title"
            :description "description"
            :short_description "short desc"
            :uri "http://example.com"
            :revision 1
            :timestamp #inst "2016-02-11T00:40:48.212-00:00"
            :schema_version schema-version
            :owner "foouser"
            :language "language"
            :tlp "green"
            :source "source"
            :source_uri "http://example.com"
            :relationship_type "targets"
            :source_ref "http://example.com"
            :target_ref "http://example.com"}
           (dissoc relationship
                   :id
                   :created
                   :modified)))

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
                :uri "http://example.com"
                :revision 1
                :schema_version schema-version
                :owner "foouser"
                :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                :language "language"
                :tlp "green"
                :source "source"
                :source_uri "http://example.com"
                :relationship_type "targets"
                :source_ref "http://example.com"
                :target_ref "http://example.com"}
               (dissoc relationship
                       :created
                       :modified)))))

      ;;(test-query-string-search :relationship "description" :description)
      
      (testing "GET /ctia/relationship/external_id"
        (let [response (get "ctia/relationship/external_id"
                            :headers {"api_key" "45c1f5e3f05d0"}
                            :query-params {"external_id" (rand-nth relationship-external-ids)})
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
                 :uri "http://example.com"
                 :revision 1
                 :schema_version schema-version
                 :owner "foouser"
                 :timestamp #inst "2016-02-11T00:40:48.212-00:00"
                 :language "language"
                 :tlp "green"
                 :source "source"
                 :source_uri "http://example.com"
                 :relationship_type "targets"
                 :source_ref "http://example.com"
                 :target_ref "http://example.com"}]
               (map #(dissoc % :created :modified) relationships)))))

      (testing "DELETE /ctia/relationship/:id"
        (let [response (delete (str "ctia/relationship/" (:short-id relationship-id))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/relationship/" (:short-id relationship-id))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
