(ns ctia.http.routes.tool-test
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
            [ctim.examples.tools :as ex]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-tool-routes
  (helpers/set-capabilities! "foouser"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (testing "POST /ctia/tool"
    (let [{status :status
           tool :parsed-body}
          (post "ctia/tool"
                :body {:external_ids ["http://ex.tld/ctia/tool/tool-123"
                                      "http://ex.tld/ctia/tool/tool-456"]
                       :name "tool"
                       :description "description"
                       :labels ["tool"]}
                :headers {"Authorization" "45c1f5e3f05d0"})

          tool-id
          (id/long-id->id (:id tool))

          tool-external-ids
          (:external_ids tool)]
      (is (= 201 status))
      (is (deep=
           {:external_ids ["http://ex.tld/ctia/tool/tool-123"
                           "http://ex.tld/ctia/tool/tool-456"]
            :type "tool"
            :name "tool"
            :description "description"
            :labels ["tool"]
            :schema_version schema-version
            :tlp "green"}
           (dissoc tool
                   :id)))

      (testing "the tool ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    tool-id)      (:hostname    show-props)))
          (is (= (:protocol    tool-id)      (:protocol    show-props)))
          (is (= (:port        tool-id)      (:port        show-props)))
          (is (= (:path-prefix tool-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/tool/:id"
        (let [response (get (str "ctia/tool/" (:short-id tool-id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              tool (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id tool-id)
                :external_ids ["http://ex.tld/ctia/tool/tool-123"
                               "http://ex.tld/ctia/tool/tool-456"]
                :type "tool"
                :name "tool"
                :description "description"
                :labels ["tool"]
                :schema_version schema-version
                :tlp "green"}
               tool))))

      (test-query-string-search :tool "description" :description)

      (testing "GET /ctia/tool/external_id/:external_id"
        (let [response (get (format "ctia/tool/external_id/%s"
                                    (encode (rand-nth tool-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              tools (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id tool-id)
                 :external_ids ["http://ex.tld/ctia/tool/tool-123"
                                "http://ex.tld/ctia/tool/tool-456"]
                 :type "tool"
                 :name "tool"
                 :description "description"
                 :labels ["tool"]
                 :schema_version schema-version
                 :tlp "green"}]
               tools))))

      (testing "PUT /ctia/tool/:id"
        (let [response (put (str "ctia/tool/" (:short-id tool-id))
                            :body {:external_ids ["http://ex.tld/ctia/tool/tool-123"
                                                  "http://ex.tld/ctia/tool/tool-456"]
                                   :name "modified tool"
                                   :description "modified description"
                                   :labels ["modified label"]}
                            :headers {"Authorization" "45c1f5e3f05d0"})
              updated-tool (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id tool-id)
                :external_ids ["http://ex.tld/ctia/tool/tool-123"
                               "http://ex.tld/ctia/tool/tool-456"]
                :type "tool"
                :name "modified tool"
                :description "modified description"
                :labels ["modified label"]
                :schema_version schema-version
                :tlp "green"}
               updated-tool))))

      (testing "PUT invalid /ctia/tool/:id"
        (let [{status :status
               body :body}
              (put (str "ctia/tool/" (:short-id tool-id))
                   :body {:external_ids ["http://ex.tld/ctia/tool/tool-123"
                                         "http://ex.tld/ctia/tool/tool-456"]
                          ;; This field has an invalid length
                          :name (apply str (repeatedly 1025 (constantly \0)))
                          :description "updated description"
                          :labels ["modified label"]
                          :type "tool"}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= status 400))
          (is (re-find #"error.*in.*name" (str/lower-case body)))))

      (testing "DELETE /ctia/tool/:id"
        (let [response (delete (str "ctia/tool/" (:short-id tool-id))
                               :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/tool/" (:short-id tool-id))
                              :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))))

  (testing "POST invalid /ctia/tool"
    (let [{status :status
           body :body}
          (post "ctia/tool"
                :body (assoc ex/new-tool-minimal
                             ;; This field has an invalid length
                             :name (clojure.string/join (repeatedly 1025 (constantly \0))))
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*name" (str/lower-case body))))))

(deftest-for-each-store test-tool-routes-access-control
  (access-control-test "tool"
                       ex/new-tool-minimal
                       true
                       true))
