(ns ctia.http.routes.attack-pattern-test
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
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [search :refer [test-query-string-search]]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]
            [ctim.examples.attack-patterns :as ex]))

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
                :body (assoc ex/new-attack-pattern-minimal
                             ;; This field has an invalid length
                             :name (clojure.string/join (repeatedly 1025 (constantly \0))))
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*name" (str/lower-case body))))))

(deftest-for-each-store test-attack-pattern-routes-access-control
  (access-control-test "attack-pattern"
                       ex/new-attack-pattern-minimal
                       true
                       true))
