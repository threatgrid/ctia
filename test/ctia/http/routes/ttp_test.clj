(ns ctia.http.routes.ttp-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers
             [core :as mht]
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
            [ctim.examples.ttps :as ex]))

(use-fixtures :once (join-fixtures [mht/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-ttp-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (helpers/set-capabilities! "baruser" "user" #{})
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")
  (whoami-helpers/set-whoami-response "2222222222222" "baruser" "user")

  (testing "POST /ctia/ttp"
    (let [{status :status
           ttp :parsed-body}
          (post "ctia/ttp"
                :body {:external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                                      "http://ex.tld/ctia/ttp/ttp-345"]
                       :title "ttp"
                       :description "description"
                       :ttp_type "foo"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                    :end_time "2016-07-11T00:40:48.212-00:00"}}
                :headers {"api_key" "45c1f5e3f05d0"})

          ttp-id (id/long-id->id (:id ttp))
          ttp-external-ids (:external_ids ttp)]
      (is (= 201 status))
      (is (deep=
           {:id (id/long-id ttp-id)
            :external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                           "http://ex.tld/ctia/ttp/ttp-345"]
            :type "ttp"
            :title "ttp"
            :tlp "green"
            :schema_version schema-version
            :description "description"
            :ttp_type "foo"
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2016-07-11T00:40:48.212-00:00"}}
           ttp))

      (testing "the ttp ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    ttp-id)      (:hostname    show-props)))
          (is (= (:protocol    ttp-id)      (:protocol    show-props)))
          (is (= (:port        ttp-id)      (:port        show-props)))
          (is (= (:path-prefix ttp-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/ttp/external_id/:external_id"
        (let [response (get (format "ctia/ttp/external_id/%s"
                                    (encode (rand-nth ttp-external-ids)))
                            :headers {"api_key" "45c1f5e3f05d0"})
              ttps (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id ttp-id)
                 :external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                                "http://ex.tld/ctia/ttp/ttp-345"]
                 :type "ttp"
                 :title "ttp"
                 :tlp "green"
                 :schema_version schema-version
                 :description "description"
                 :ttp_type "foo"
                 :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                              :end_time #inst "2016-07-11T00:40:48.212-00:00"}}]
               ttps))))


      (test-query-string-search :ttp "description" :description)


      (testing "GET /ctia/ttp/:id"
        (let [response (get (str "ctia/ttp/" (:short-id ttp-id))
                            :headers {"api_key" "45c1f5e3f05d0"})
              ttp (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id ttp-id)
                :external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                               "http://ex.tld/ctia/ttp/ttp-345"]
                :type "ttp"
                :title "ttp"
                :tlp "green"
                :schema_version schema-version
                :description "description"
                :ttp_type "foo"
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}}
               ttp))))

      (testing "PUT /ctia/ttp/:id"
        (let [{status :status
               updated-ttp :parsed-body}
              (put (str "ctia/ttp/" (:short-id ttp-id))
                   :body {:external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                                         "http://ex.tld/ctia/ttp/ttp-345"]
                          :title "updated ttp"
                          :description "updated description"
                          :ttp_type "bar"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:id (id/long-id ttp-id)
                :external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                               "http://ex.tld/ctia/ttp/ttp-345"]
                :type "ttp"
                :title "updated ttp"
                :tlp "green"
                :schema_version schema-version
                :description "updated description"
                :ttp_type "bar"
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}}
               updated-ttp))))

      (testing "PUT invalid /ctia/ttp/:id"
        (let [{status :status
               body :body}
              (put (str "ctia/ttp/" (:short-id ttp-id))
                   :body {:external_ids ["http://ex.tld/ctia/ttp/ttp-123"
                                         "http://ex.tld/ctia/ttp/ttp-345"]
                          ;; This field has an invalid length
                          :title (apply str (repeatedly 1025 (constantly \0)))
                          :description "updated description"
                          :ttp_type "bar"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= status 400))
          (is (re-find #"error.*in.*title" (str/lower-case body)))))

      (testing "DELETE /ctia/ttp/:id"
        (let [response (delete (str "ctia/ttp/" (:short-id ttp-id))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/ttp/" (:short-id ttp-id))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))))

  (testing "POST invalid /ctia/ttp"
    (let [{status :status
           body :body}
          (post "ctia/ttp"
                :body (assoc ex/new-ttp-minimal
                              ;; This field has an invalid length
                             :title (apply str (repeatedly 1025 (constantly \0))))
                :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*title" (str/lower-case body))))))
