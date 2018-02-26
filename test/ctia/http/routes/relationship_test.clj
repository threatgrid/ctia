(ns ctia.http.routes.relationship-test
  (:refer-clojure :exclude [get])
  (:require
   [clojure.string :as str]
   [ctim.examples.relationships
    :refer [new-relationship-maximal
            new-relationship-minimal]]
   [ctia.schemas.sorting
    :refer [relationship-sort-fields]]
   [clj-momo.test-helpers
    [core :as mth]
    [http :refer [encode]]]
   [clojure.test :refer [is join-fixtures testing use-fixtures]]
   [ctia.domain.entities :refer [schema-version]]
   [ctia.properties :refer [get-http-show]]
   [ctia.test-helpers
    [http :refer [doc-id->rel-url]]
    [search :refer [test-query-string-search]]
    [access-control :refer [access-control-test]]
    [auth :refer [all-capabilities]]
    [core :as helpers :refer [delete get post put]]
    [fake-whoami-service :as whoami-helpers]
    [pagination :refer [pagination-test]]
    [field-selection :refer [field-selection-tests]]
    [store :refer [deftest-for-each-store]]]
   [ctim.domain.id :as id]
   [ctim.examples.relationships :refer [new-relationship-minimal
                                        new-relationship-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-relationship-routes-bad-reference
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (testing "POST /cita/relationship"
    (let [new-relationship (-> new-relationship-maximal
                               (assoc
                                :source_ref "http://example.com/"
                                :target_ref "http://example.com/"
                                :external_ids
                                ["http://ex.tld/ctia/relationship/relationship-123"
                                 "http://ex.tld/ctia/relationship/relationship-456"])
                               (dissoc :id))
          {status :status
           {error :error} :parsed-body}
          (post "ctia/relationship"
                :body new-relationship
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= 400 status)))))

(deftest-for-each-store test-relationship-routes
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (testing "POST /ctia/relationship"
    (let [new-relationship
          (-> new-relationship-maximal
              (assoc
               :source_ref (str "http://example.com/ctia/judgement/judgement-"
                                "f9832ac2-ee90-4e18-9ce6-0c4e4ff61a7a")
               :target_ref (str "http://example.com/ctia/indicator/indicator-"
                                "8c94ca8d-fb2b-4556-8517-8e6923d8d3c7")
               :external_ids
               ["http://ex.tld/ctia/relationship/relationship-123"
                "http://ex.tld/ctia/relationship/relationship-456"])
              (dissoc :id))
          {status :status
           relationship :parsed-body}
          (post "ctia/relationship"
                :body new-relationship
                :headers {"Authorization" "45c1f5e3f05d0"})
          relationship-id (id/long-id->id (:id relationship))
          relationship-external-ids
          (:external_ids relationship)]
      (is (= 201 status))
      (is (deep=
           (assoc new-relationship :id (id/long-id relationship-id))
           relationship))

      (testing "the relationship ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    relationship-id)      (:hostname    show-props)))
          (is (= (:protocol    relationship-id)      (:protocol    show-props)))
          (is (= (:port        relationship-id)      (:port        show-props)))
          (is (= (:path-prefix relationship-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/relationship/:id"
        (let [response (get (str "ctia/relationship/" (:short-id relationship-id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              relationship (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               (assoc new-relationship :id (id/long-id relationship-id))
               relationship))))

      (test-query-string-search :relationship "description" :description)

      (testing "GET /ctia/relationship/external_id/:external_id"
        (let [response (get (format "ctia/relationship/external_id/%s"
                                    (encode (rand-nth relationship-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              relationships (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [(assoc new-relationship :id (id/long-id relationship-id))]
               relationships))))

      (testing "PUT /ctia/relationship/:id"
        (let [with-updates (assoc relationship
                                  :title "modified relationship")
              response (put (str "ctia/relationship/" (:short-id relationship-id))
                            :body with-updates
                            :headers {"Authorization" "45c1f5e3f05d0"})
              updated-relationship (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               with-updates
               updated-relationship))))

      (testing "PUT invalid /ctia/relationship/:id"
        (let [{status :status
               body :body}
              (put (str "ctia/relationship/" (:short-id relationship-id))
                   :body (assoc relationship
                                :title (clojure.string/join
                                        (repeatedly 1025 (constantly \0))))
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= status 400))
          (is (re-find #"error.*in.*title" (str/lower-case body)))))


      (testing "DELETE /ctia/relationship/:id"
        (let [response (delete (str "ctia/relationship/" (:short-id relationship-id))
                               :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/relationship/" (:short-id relationship-id))
                              :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))

(deftest-for-each-store test-relationship-pagination-field-selection
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (let [posted-docs
        (doall (map #(:parsed-body
                      (post "ctia/relationship"
                            :body (-> new-relationship-maximal
                                      (dissoc :id)
                                      (assoc :source (str "dotimes " %)))
                            :headers {"Authorization" "45c1f5e3f05d0"}))
                    (range 0 30)))]

    (pagination-test
     "ctia/relationship/search?query=*"
     {"Authorization" "45c1f5e3f05d0"}
     relationship-sort-fields)

    (field-selection-tests
     ["ctia/relationship/search?query=*"
      (-> posted-docs first :id doc-id->rel-url)]
     {"Authorization" "45c1f5e3f05d0"}
     relationship-sort-fields)))

(deftest-for-each-store test-relationship-routes-access-control
  (access-control-test "relationship"
                       new-relationship-minimal
                       true
                       true))
