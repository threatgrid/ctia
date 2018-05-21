(ns ctia.test-helpers.crud
  (:refer-clojure :exclude [get])
  (:require
   [ctia.domain.entities :refer [schema-version]]
   [clj-momo.test-helpers.http :refer [encode]]
   [clojure
    [string :as str]
    [test :refer [is testing]]]
   [ctia.properties :refer [get-http-show]]
   [ctia.test-helpers
    [core :as helpers :refer [delete get post put patch]]
    [search :refer [test-query-string-search]]]
   [ctim.domain.id :as id]))

(defn entity-crud-test
  [{:keys [entity
           example
           headers
           update-field
           search-field
           invalid-tests?
           invalid-test-field
           update-tests?
           patch-tests?
           search-tests?
           additional-tests]
    :or {invalid-tests? true
         invalid-test-field :title
         update-field :title
         search-field :description
         update-tests? true
         patch-tests? false
         search-tests? true}}]
  (testing (str "POST /ctia/" entity)
    (let [new-record (dissoc example :id)
          {status :status
           record :parsed-body}
          (post (str "ctia/" entity)
                :body new-record
                :headers headers)
          record-id
          (id/long-id->id (:id record))
          record-external-ids
          (:external_ids record)]
      (is (= 201 status))
      (is (deep=
           (assoc new-record :id (id/long-id record-id)) record))

      (testing (format "the %s ID has correct fields" entity)
        (let [show-props (get-http-show)]
          (is (= (:hostname record-id)    (:hostname show-props)))
          (is (= (:protocol record-id)    (:protocol show-props)))
          (is (= (:port record-id)        (:port show-props)))
          (is (= (:path-prefix record-id) (seq (:path-prefix show-props))))))

      (testing (format "GET /ctia/%s/:id" entity)
        (let [response (get (format "ctia/%s/%s" entity (:short-id record-id))
                            :headers headers)
              actor (:parsed-body response)]
          (is (= 200 (:status response)))

          (let [expected (assoc new-record :id (id/long-id record-id))]
            (is (deep=
                 expected
                 record)))))

      (when search-tests?
        (test-query-string-search search-field
                                  (name search-field)
                                  search-field ))

      (testing (format "GET /ctia/%s/external_id/:external_id" entity)
        (let [response (get (format "ctia/%s/external_id/%s"
                                    entity (encode (rand-nth record-external-ids)))
                            :headers headers)
              records (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [(assoc record :id (id/long-id record-id))]
               records))))

      (testing (format "PATCH /ctia/%s/:id" entity)
        (let [updates {update-field "patch"}
              response (patch (format "ctia/%s/%s" entity (:short-id record-id))
                              :body updates
                              :headers headers)
              updated-record (:parsed-body response)]
          (when patch-tests?
            (is (= 200 (:status response)))
            (is (deep=
                 (merge record updates)
                 updated-record)))))

      (testing (format "PUT /ctia/%s/:id" entity)
        (let [with-updates (assoc record
                                  update-field "modified")
              response (put (format "ctia/%s/%s" entity (:short-id record-id))
                            :body with-updates
                            :headers headers)
              updated-record (:parsed-body response)]

          (when update-tests?
            (is (= 200 (:status response)))
            (is (deep=
                 with-updates
                 updated-record)))

          ;; execute entity custom tests before deleting the fixture
          (testing "additional tests"
            (when additional-tests
              (additional-tests record-id
                                (if update-tests?
                                  updated-record
                                  record))))))

      (when invalid-tests?
        (testing (format "PUT invalid /ctia/%s/:id" entity)
          (let [{status :status
                 body :body}
                (put (format "ctia/%s/%s" entity (:short-id record-id))
                     :body (assoc record invalid-test-field (str/join
                                                             (repeatedly 1025 (constantly \0))))
                     :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= status 400))
            (is (re-find (re-pattern
                          (str "error.*in.*"
                               (name invalid-test-field)))
                         (str/lower-case body))))))

      (testing (format "DELETE non-existant /ctia/%s/:id" entity)
        (let [response (delete (format "ctia/%s/%s-42424242" entity entity )
                               :headers headers)]
          (is (= 404 (:status response)))))

      (testing (format "DELETE /ctia/%s/:id" entity)
        (let [response (delete (format "ctia/%s/%s" entity (:short-id record-id))
                               :headers headers)]
          (is (= 204 (:status response)))
          (let [response (get (format "ctia/%s/%s" entity (:short-id record-id))
                              :headers headers)]
            (is (= 404 (:status response)))))))

    (when invalid-tests?
      (testing (format "POST invalid /ctia/%s :schema_version should be ignored" entity)
        (let [{status :status
               record :parsed-body}
              (post (str "ctia/" entity)
                    ;; This record has an outdated schema_version
                    :body (-> example
                              (dissoc :id)
                              (assoc :schema_version "0.4.2"))
                    :headers headers)]
          (is (= status 201))
          (is (= (:schema_version record)
                 schema-version))

          (when update-tests?
            (let [id (id/long-id->id (:id record))
                  {status :status
                   updated-record :parsed-body}
                  (put (format "ctia/%s/%s" entity (:short-id id))
                       ;; This update has an outdated schema_version
                       :body (-> record
                                 (assoc :schema_version "0.4.2"))
                       :headers headers)]
              (is (= status 200))
              (is (= (:schema_version updated-record)
                     schema-version))))))

      (testing (format "POST invalid /ctia/%s" entity)
        (let [{status :status
               body :body}
              (post (str "ctia/" entity)
                    ;; This field has an invalid length
                    :body (assoc example
                                 invalid-test-field (str/join (repeatedly 1025 (constantly \0))))
                    :headers headers)]
          (is (= status 400))
          (is (re-find (re-pattern
                        (str "error.*in.*" (name invalid-test-field)))
                       (str/lower-case body))))))))
