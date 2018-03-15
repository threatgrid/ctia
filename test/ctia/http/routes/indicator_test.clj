(ns ctia.http.routes.indicator-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers
             [core :as mth]
             [http :refer [encode]]]
            [clojure
             [string :as str]
             [test :refer [is join-fixtures testing use-fixtures]]]
            [ctia
             [auth :as auth]
             [properties :refer [get-http-show]]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers
             [core :as helpers :refer [delete get post put fake-long-id]]
             [access-control
              :refer [access-control-test]]
             [fake-whoami-service :as whoami-helpers]
             [search :refer [test-query-string-search]]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]
            [ctim.examples.indicators :refer [new-indicator-maximal
                                              new-indicator-minimal]]
            [ring.util.codec :refer [url-encode]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-indicator-routes
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" auth/all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (testing "POST /ctia/indicator"
    (let [new-indicator (-> new-indicator-maximal
                            (dissoc :id)
                            (assoc
                             :external_ids ["http://ex.tld/ctia/indicator/indicator-123"
                                            "http://ex.tld/ctia/indicator/indicator-345"]
                             :composite_indicator_expression
                             {:operator "and"
                              :indicator_ids [(fake-long-id 'indicator 1)
                                              (fake-long-id 'indicator 2)]}))
          {status :status
           indicator :parsed-body}
          (post "ctia/indicator"
                :body new-indicator
                :headers {"Authorization" "45c1f5e3f05d0"})

          indicator-id (id/long-id->id (:id indicator))
          indicator-external-ids (:external_ids indicator)]
      (is (= 201 status))
      (is (deep=
           (assoc new-indicator :id (id/long-id indicator-id))
           indicator))

      (testing "the indicator ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    indicator-id)      (:hostname    show-props)))
          (is (= (:protocol    indicator-id)      (:protocol    show-props)))
          (is (= (:port        indicator-id)      (:port        show-props)))
          (is (= (:path-prefix indicator-id) (seq (:path-prefix show-props))))))

      (test-query-string-search :indicator "description" :description)

      (testing "GET /ctia/indicator/external_id/:external_id"
        (let [response (get (format "ctia/indicator/external_id/%s"
                                    (encode (rand-nth indicator-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              indicators (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [(assoc new-indicator :id (id/long-id indicator-id))]
               indicators))))

      (testing "GET /ctia/indicator/:id"
        (let [response (get (str "ctia/indicator/" (:short-id indicator-id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              indicator (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               (assoc new-indicator :id (id/long-id indicator-id))
               indicator))))

      (testing "PUT /ctia/indicator/:id"
        (let [with-updates (-> indicator
                               (assoc :title "modified indicator")
                               (assoc-in [:valid_time :end_time]
                                         #inst "2042-02-12T00:00:00.000-00:00"))
              {status :status
               updated-indicator :parsed-body}
              (put (str "ctia/indicator/" (:short-id indicator-id))
                   :body with-updates
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               with-updates
               updated-indicator))))

      (testing "PUT invalid /ctia/indicator/:id"
        (let [{status :status
               body :body}
              (put (str "ctia/indicator/" (:short-id indicator-id))
                   :body (assoc indicator
                                :title (clojure.string/join
                                        (repeatedly 1025 (constantly \0))))
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= status 400))
          (is (re-find #"error.*in.*title" (str/lower-case body)))))

      (testing "DELETE /ctia/indicator/:id"
        (let [response (delete (str "ctia/indicator/" (:short-id indicator-id))
                               :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/indicator/" (:short-id indicator-id))
                              :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))))

  (testing "POST invalid /ctia/indicator"
    (let [{status :status
           body :body}
          (post "ctia/indicator"
                :body (assoc new-indicator-minimal
                             ;; This field has an invalid length
                             :title (apply str (repeatedly 1025 (constantly \0))))
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*title" (str/lower-case body))))))

(deftest-for-each-store test-indicator-routes-access-control
  (access-control-test "indicator"
                       new-indicator-minimal
                       true
                       false))
