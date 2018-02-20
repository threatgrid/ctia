(ns ctia.http.routes.coa-test
  (:refer-clojure :exclude [get])
  (:require [ctim.examples.coas
             :refer [new-coa-minimal new-coa-maximal]]
            [ctia.schemas.sorting
             :refer [coa-sort-fields]]
            [clj-momo.test-helpers.core :as mth]
            [clj-momo.test-helpers.http :refer [encode]]
            [clojure
             [string :as str]
             [test :refer [is join-fixtures testing use-fixtures]]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [http :refer [doc-id->rel-url]]
             [access-control :refer [access-control-test]]
             [search :refer [test-query-string-search]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [pagination :refer [pagination-test]]
             [field-selection :refer [field-selection-tests]]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-coa-routes
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (testing "POST /ctia/coa"
    (let [{status :status
           coa :parsed-body}
          (post "ctia/coa"
                :body (dissoc new-coa-maximal :id)
                :headers {"Authorization" "45c1f5e3f05d0"})

          coa-id (id/long-id->id (:id coa))
          coa-external-ids (:external_ids coa)]
      (is (= 201 status))
      (is (deep= (assoc new-coa-maximal :id (id/long-id coa-id)) coa))

      (testing "the coa ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    coa-id)      (:hostname    show-props)))
          (is (= (:protocol    coa-id)      (:protocol    show-props)))
          (is (= (:port        coa-id)      (:port        show-props)))
          (is (= (:path-prefix coa-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/coa/external_id/:external_id"
        (let [response (get (format "ctia/coa/external_id/%s" (encode (rand-nth coa-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              coas (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [(assoc new-coa-maximal :id (id/long-id coa-id))]
               coas))))

      (testing "GET /ctia/coa/:id"
        (let [response (get (str "ctia/coa/" (:short-id coa-id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              coa (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               (assoc new-coa-maximal :id (id/long-id coa-id))
               coa))))

      (test-query-string-search :coa "description" :description)

      (testing "PUT /ctia/coa/:id"
        (let [with-updates (assoc coa
                                  :title "updated coa"
                                  :description "updated description")
              {updated-coa :parsed-body
               status :status}
              (put (str "ctia/coa/" (:short-id coa-id))
                   :body with-updates
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep= with-updates updated-coa))))

      (testing "PUT invalid /ctia/coa/:id"
        (let [{status :status
               body :body}
              (put (str "ctia/coa/" (:short-id coa-id))
                   ;; use an invalid length
                   :body (assoc coa
                                :title (clojure.string/join
                                        (repeatedly 1025 (constantly \0))))
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= status 400))
          (is (re-find #"error.*in.*title" (str/lower-case body)))))

      (testing "DELETE /ctia/coa/:id"
        (let [response (delete (str "/ctia/coa/" (:short-id coa-id))
                               :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "/ctia/coa/" (:short-id coa-id))
                              :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))))

  (testing "POST invalid /ctia/coa"
    (let [{status :status
           body :body}
          (post "ctia/coa"
                ;; This field has an invalid length
                :body (assoc new-coa-minimal
                             :title (clojure.string/join
                                     (repeatedly 1025 (constantly \0))))
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*title" (str/lower-case body))))))

(deftest-for-each-store test-coa-pagination-field-selection
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (let [posted-docs
        (doall (map #(:parsed-body
                      (post "ctia/coa"
                            :body
                            (-> new-coa-maximal
                                (dissoc :id)
                                (assoc :source (str "dotimes " %)))
                            :headers {"Authorization" "45c1f5e3f05d0"}))
                    (range 0 30)))]

    (pagination-test
     "ctia/coa/search?query=*"
     {"Authorization" "45c1f5e3f05d0"}
     coa-sort-fields)

    (field-selection-tests
     ["ctia/coa/search?query=*"
      (-> posted-docs first :id doc-id->rel-url)]
     {"Authorization" "45c1f5e3f05d0"}
     coa-sort-fields)))

(deftest-for-each-store test-coa-routes-access-control
  (access-control-test "coa"
                       new-coa-minimal
                       true
                       true))
