(ns ctia.http.routes.attack-pattern-test
  (:refer-clojure :exclude [get])
  (:require
   [ctim.examples.attack-patterns
    :refer [new-attack-pattern-minimal
            new-attack-pattern-maximal]]
   [ctia.schemas.sorting
    :refer [attack-pattern-sort-fields]]
   [clj-momo.test-helpers
    [core :as mth]
    [http :refer [encode]]]
   [clojure
    [string :as str]
    [test :refer [is join-fixtures testing use-fixtures]]]
   [ctia.domain.entities :refer [schema-version]]
   [ctia.properties :refer [get-http-show]]
   [ctia.test-helpers
    [http :refer [doc-id->rel-url]]
    [access-control :refer [access-control-test]]
    [auth :refer [all-capabilities]]
    [core :as helpers :refer [delete get post put]]
    [fake-whoami-service :as whoami-helpers]
    [pagination :refer [pagination-test]]
    [field-selection :refer [field-selection-tests]]
    [search :refer [test-query-string-search]]
    [store :refer [deftest-for-each-store]]]
   [ctim.domain.id :as id]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-attack-pattern-routes
  (helpers/set-capabilities! "foouser"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (testing "POST /ctia/attack-pattern"
    (let [{status :status
           attack-pattern :parsed-body}
          (post "ctia/attack-pattern"
                :body {:external_ids ["http://ex.tld/ctia/attack-pattern/attack-pattern-123"
                                      "http://ex.tld/ctia/attack-pattern/attack-pattern-456"]
                       :name "attack-pattern-name"
                       :description "description"}
                :headers {"Authorization" "45c1f5e3f05d0"})

          attack-pattern-id
          (id/long-id->id (:id attack-pattern))

          attack-pattern-external-ids
          (:external_ids attack-pattern)]
      (is (= 201 status))
      (is (deep=
           {:external_ids ["http://ex.tld/ctia/attack-pattern/attack-pattern-123"
                           "http://ex.tld/ctia/attack-pattern/attack-pattern-456"]
            :type "attack-pattern"
            :name "attack-pattern-name"
            :description "description",
            :schema_version schema-version
            :tlp "green"}
           (dissoc attack-pattern
                   :id)))

      (testing "the attack-pattern ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    attack-pattern-id)      (:hostname    show-props)))
          (is (= (:protocol    attack-pattern-id)      (:protocol    show-props)))
          (is (= (:port        attack-pattern-id)      (:port        show-props)))
          (is (= (:path-prefix attack-pattern-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/attack-pattern/:id"
        (let [response (get (str "ctia/attack-pattern/" (:short-id attack-pattern-id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              attack-pattern (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id attack-pattern-id)
                :external_ids ["http://ex.tld/ctia/attack-pattern/attack-pattern-123"
                               "http://ex.tld/ctia/attack-pattern/attack-pattern-456"]
                :type "attack-pattern"
                :name "attack-pattern-name"
                :description "description"
                :schema_version schema-version
                :tlp "green"}
               attack-pattern))))

      (test-query-string-search :attack-pattern "description" :description)

      (testing "GET /ctia/attack-pattern/external_id/:external_id"
        (let [response (get (format "ctia/attack-pattern/external_id/%s"
                                    (encode (rand-nth attack-pattern-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              attack-patterns (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id attack-pattern-id)
                 :external_ids ["http://ex.tld/ctia/attack-pattern/attack-pattern-123"
                                "http://ex.tld/ctia/attack-pattern/attack-pattern-456"]
                 :type "attack-pattern"
                 :name "attack-pattern-name"
                 :description "description",
                 :schema_version schema-version
                 :tlp "green"}]
               attack-patterns))))

      (testing "PUT /ctia/attack-pattern/:id"
        (let [response (put (str "ctia/attack-pattern/" (:short-id attack-pattern-id))
                            :body {:external_ids ["http://ex.tld/ctia/attack-pattern/attack-pattern-123"
                                                  "http://ex.tld/ctia/attack-pattern/attack-pattern-456"]
                                   :name "modified attack-pattern"
                                   :description "updated description"
                                   :type "attack-pattern"}
                            :headers {"Authorization" "45c1f5e3f05d0"})
              updated-attack-pattern (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id attack-pattern-id)
                :external_ids ["http://ex.tld/ctia/attack-pattern/attack-pattern-123"
                               "http://ex.tld/ctia/attack-pattern/attack-pattern-456"]
                :type "attack-pattern"
                :name "modified attack-pattern"
                :description "updated description"
                :schema_version schema-version
                :tlp "green"}
               updated-attack-pattern))))

      (testing "PUT invalid /ctia/attack-pattern/:id"
        (let [{status :status
               body :body}
              (put (str "ctia/attack-pattern/" (:short-id attack-pattern-id))
                   :body {:external_ids ["http://ex.tld/ctia/attack-pattern/attack-pattern-123"
                                         "http://ex.tld/ctia/attack-pattern/attack-pattern-456"]
                          ;; This field has an invalid length
                          :name (apply str (repeatedly 1025 (constantly \0)))
                          :description "updated description"
                          :type "attack-pattern"}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= status 400))
          (is (re-find #"error.*in.*name" (str/lower-case body)))))

      (testing "DELETE /ctia/attack-pattern/:id"
        (let [response (delete (str "ctia/attack-pattern/" (:short-id attack-pattern-id))
                               :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/attack-pattern/" (:short-id attack-pattern-id))
                              :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))))

  (testing "POST invalid /ctia/attack-pattern"
    (let [{status :status
           body :body}
          (post "ctia/attack-pattern"
                :body (assoc new-attack-pattern-minimal
                             ;; This field has an invalid length
                             :name (clojure.string/join (repeatedly 1025 (constantly \0))))
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*name" (str/lower-case body))))))

(deftest-for-each-store test-attack-pattern-pagination-field-selection
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (let [posted-docs
        (doall (map #(:parsed-body
                      (post "ctia/attack-pattern"
                            :body (-> new-attack-pattern-maximal
                                      (dissoc :id)
                                      (assoc :description (str "dotimes " %)))
                            :headers {"Authorization" "45c1f5e3f05d0"}))
                    (range 0 30)))]

    (pagination-test
     "ctia/attack-pattern/search?query=*"
     {"Authorization" "45c1f5e3f05d0"}
     attack-pattern-sort-fields)

    (field-selection-tests
     ["ctia/attack-pattern/search?query=*"
      (-> posted-docs first :id doc-id->rel-url)]
     {"Authorization" "45c1f5e3f05d0"}
     attack-pattern-sort-fields)))

(deftest-for-each-store test-attack-pattern-routes-access-control
  (access-control-test "attack-pattern"
                       new-attack-pattern-minimal
                       true
                       true))
