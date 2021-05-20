(ns ctia.test-helpers.crud
  (:require
   [cheshire.core :refer [parse-string]]
   [clj-http.fake :refer [with-global-fake-routes]]
   [clj-momo.lib.clj-time.coerce :as tc]
   [clj-momo.test-helpers.http :refer [encode]]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer [is testing]]
   [ctia.domain.entities :refer [schema-version]]
   [ctia.properties :as p :refer [get-http-show]]
   [ctia.test-helpers.core :as helpers
    :refer [DELETE entity->short-id GET PATCH POST PUT]]
   [ctia.test-helpers.http :refer [app->HTTPShowServices]]
   [ctia.test-helpers.search :as th.search]
   [ctim.domain.id :as id])
  (:import [java.util UUID]))

(defn crud-wait-for-test
  [{:keys [app
           entity
           example
           headers
           delete-search-tests?
           update-field
           update-tests?
           patch-tests?
           revoke-tests?
           revoke-tests-extra-query-params]
    :or {update-field :title
         update-tests? true
         patch-tests? true
         delete-search-tests? true}}]
  (assert app)
  (let [get-in-config (helpers/current-get-in-config-fn app)
        entity-str (name entity)
        new-record (dissoc example :id)
        es-params (volatile! nil)
        simple-handler (fn [body]
                         (fn [{:keys [url query-string]}]
                           (vreset! es-params query-string)
                           {:status 200
                            :headers {"Content-Type" "application/json"}
                            :body body}))
        bulk-routes {#".*_bulk.*"
                     {:post (fn [{:keys [query-string body]}]
                              (let [mapping-type (-> (io/reader body)
                                                     line-seq
                                                     first
                                                     (parse-string true)
                                                     (get-in [:index :_type]))]
                                (when-not (= "event" mapping-type)
                                  (vreset! es-params query-string))
                                {:status 200
                                 :headers {"Content-Type" "application/json"}
                                 :body "{}"}))}}
        check-refresh (fn [wait_for msg]
                        (let [default-es-refresh (get-in-config [:ctia :store :es :default :refresh])
                              expected (str "refresh="
                                            (case wait_for
                                              nil default-es-refresh
                                              true "wait_for"
                                              false "false"))]
                          (is (some-> @es-params
                                      (string/includes? expected))
                              (format "%s (expected %s, actual: %s)" msg expected @es-params))
                          (vreset! es-params nil)))
        new-entity-short-id #(-> (POST app
                                       (format "ctia/%s?wait_for=true" entity-str)
                                       :body new-record
                                       :headers headers)
                                 :parsed-body
                                 entity->short-id)]
    (testing "testing wait_for values on entity creation"
      (let [test-create (fn [wait_for msg]
                          (let [path (cond-> (str "ctia/" entity-str)
                                       (boolean? wait_for) (str "?wait_for=" wait_for))]
                            (with-global-fake-routes bulk-routes
                              (POST app
                                    path
                                    :body new-record
                                    :headers headers))
                            (check-refresh wait_for msg)))]
        (test-create true
                     "Create queries should wait for index refresh when wait_for is true")
        (test-create false
                     (str "Create queries should not wait for index refresh when "
                          "wait_for is false"))
        (test-create nil
                     (str "Configured ctia.store.es.default.refresh value is applied "
                          "when wait_for is not specified"))))

    (testing "testing wait_for values on entity update / patch"
      (let [entity-id (new-entity-short-id)
            test-modify (fn [method-kw wait_for msg]
                          (let [method (case method-kw
                                         :PUT PUT
                                         :PATCH PATCH)
                                path (cond-> (format "ctia/%s/%s" entity-str entity-id)
                                       (boolean? wait_for) (str "?wait_for=" wait_for))
                                updates (cond->> {update-field "modified"}
                                          (= :PUT method-kw) (into new-record))
                                es-index-uri-pattern (re-pattern (str ".*920.*" entity-id ".*"))]
                            (with-global-fake-routes {es-index-uri-pattern {:post (simple-handler "{}")}}
                              (method app
                                      path
                                      :body updates
                                      :headers headers))
                            (check-refresh wait_for msg)))]
        (when update-tests?
          (test-modify :PUT true
                       (str "Update queries should wait for index refresh when "
                            "wait_for is true"))
          (test-modify :PUT false
                       (str "Update queries should not wait for index refresh "
                            "when wait_for is false"))
          (test-modify :PUT nil
                       (str "Configured ctia.store.es.default.refresh value "
                            "is applied when wait_for is not specified")))
        (when patch-tests?
          (test-modify :PATCH true
                       (str "Patch queries should wait for index refresh when "
                            "wait_for is true"))
          (test-modify :PATCH false
                       (str "Patch queries should not wait for index refresh "
                            "when wait_for is false"))
          (test-modify :PATCH nil
                       (str "Configured ctia.store.es.default.refresh value is "
                            "applied when wait_for is not specified")))))

    (testing "testing wait_for values on entity deletion"
      (let [test-delete (fn [wait_for msg]
                          (let [entity-id (new-entity-short-id)
                                es-index-uri-pattern (re-pattern (str ".*920.*" entity-id ".*"))
                                path (cond-> (format "ctia/%s/%s" entity-str entity-id)
                                       (boolean? wait_for) (str "?wait_for=" wait_for))]
                            (with-global-fake-routes {es-index-uri-pattern {:delete (simple-handler "{}")}}
                              (DELETE app
                                      path
                                      :headers headers))
                            (check-refresh wait_for msg)))]
        (test-delete true
                     (str "Delete queries should wait for index refresh when "
                          "wait_for is true"))
        (test-delete false
                     (str "Delete queries should not wait for index refresh "
                          "when wait_for is false"))
        (test-delete nil
                     (str "Configured ctia.store.es.default.refresh value is "
                          "applied when wait_for is not specified"))))
    (when revoke-tests?
      (testing "testing wait_for values on entity revocation"
        (let [test-revoke (fn [wait_for msg]
                            (let [entity-id (new-entity-short-id)
                                  es-index-uri-pattern (re-pattern (str ".*920.*" entity-id ".*"))
                                  path (cond-> (format "ctia/%s/%s/expire" entity-str entity-id)
                                         (boolean? wait_for) (str "?wait_for=" wait_for))]
                              (with-global-fake-routes {es-index-uri-pattern {:post (simple-handler "{}")}}
                                (apply POST
                                       app
                                       path
                                       :headers headers
                                       (when revoke-tests-extra-query-params
                                         [:query-params revoke-tests-extra-query-params])))
                              (check-refresh wait_for msg)))]
          (test-revoke true
                       (str "Revoke queries should wait for index refresh when "
                            "wait_for is true"))
          (test-revoke false
                       (str "Revoke queries should not wait for index refresh "
                            "when wait_for is false"))
          (test-revoke nil
                       (str "Configured ctia.store.es.default.refresh value is "
                            "applied when wait_for is not specified")))))
    (when delete-search-tests?
      (testing "testing wait_for values on delete search"
        (let [test-delete-search (fn [wait_for msg]
                                   (let [ctia-path (format "ctia/%s/search?REALLY_DELETE_ALL_THESE_ENTITIES=true&from=2020-01-01" entity-str)
                                         path (cond-> ctia-path
                                                (boolean? wait_for) (str "&wait_for=" wait_for))
                                         es-index-uri-pattern (re-pattern (format ".*_delete_by_query.*(%s){0}"
                                                                                  (UUID/randomUUID)))]
                                     (with-global-fake-routes {es-index-uri-pattern (simple-handler "{\"deleted\": 1}")}
                                       (DELETE app
                                               path
                                               :headers headers))
                                     (check-refresh wait_for msg)))]
          (test-delete-search true
                              (str "Delete search should wait for index refresh when "
                                   "wait_for is true"))
          (test-delete-search false
                              (str "Delete search should not wait for index refresh "
                                   "when wait_for is false"))
          (test-delete-search nil
                              (str "Configured ctia.store.es.default.refresh value is "
                                   "applied when wait_for is not specified")))))))

(defn entity-crud-test
  [{:keys [app
           entity
           plural
           example
           headers
           update-field
           search-field
           optional-field
           invalid-tests?
           invalid-test-field
           update-tests?
           patch-tests?
           search-tests?
           delete-search-tests?
           additional-tests
           search-value
           revoke-tests?
           revoke-tests-extra-query-params]
    :or {invalid-tests? true
         invalid-test-field :title
         update-field :title
         search-field :description
         optional-field :external_ids
         update-tests? true
         patch-tests? false
         search-tests? true}
    :as params}]
 (assert app "Must pass :app to entity-crud-test")
  (let [get-in-config (helpers/current-get-in-config-fn app)
        entity-str (name entity)]
    (testing (str "POST /ctia/" entity-str)
      (let [new-record (dissoc example :id)
            {post-status :status
             post-record :parsed-body}
            (POST app
                  (str "ctia/" entity-str "?wait_for=true")
                  :body new-record
                  :headers headers)
            record-id (id/long-id->id (:id post-record))
            expected (assoc post-record :id (id/long-id record-id))
            record-external-ids (:external_ids post-record)]
        (is (= 201 post-status))
        (is (= expected post-record))

        (testing (format "the %s ID has correct fields" entity-str)
          (let [show-props (get-http-show (app->HTTPShowServices app))]
            (is (= (:hostname record-id)    (:hostname show-props)))
            (is (= (:protocol record-id)    (:protocol show-props)))
            (is (= (:port record-id)        (:port show-props)))
            (is (= (:path-prefix record-id) (seq (:path-prefix show-props))))))

        (testing (format "GET /ctia/%s/:id" entity-str)
          (let [{get-status :status
                 get-record :parsed-body}
                (GET app
                     (format "ctia/%s/%s" entity-str (:short-id record-id))
                     :headers headers)]
            (is (= 200 get-status))
            (is (= expected get-record))))

        (testing (format "GET /ctia/%s/external_id/:external_id" entity-str)
          (let [response (GET app
                              (format "ctia/%s/external_id/%s"
                                      entity-str (encode (rand-nth record-external-ids)))
                              :headers headers)
                records (:parsed-body response)]
            (is (= 200 (:status response)))
            (is (= [expected] records))))

        (testing (format "PATCH /ctia/%s/:id" entity-str)
          (let [updates {update-field "patch"}
                response (PATCH app
                                (format "ctia/%s/%s" entity-str (:short-id record-id))
                                :body updates
                                :headers headers)
                updated-record (:parsed-body response)]
            (when patch-tests?
              (is (= 200 (:status response)))
              (is (= (merge post-record updates)
                     updated-record)))))

        (testing (format "PUT /ctia/%s/:id" entity-str)
          (let [with-updates (-> post-record
                                 (assoc update-field "modified")
                                 (dissoc optional-field))
                {updated-record :parsed-body
                 update-status :status}
                (PUT app
                     (format "ctia/%s/%s" entity-str (:short-id record-id))
                     :body with-updates
                     :headers headers)
                {stored-record :parsed-body}
                (GET app
                     (format "ctia/%s/%s" entity-str (:short-id record-id))
                     :headers headers)]
            (when update-tests?
              (is (= 200 update-status))
              (is (= with-updates
                     updated-record))
              (is (= updated-record
                     stored-record)))

            (when revoke-tests?
              (testing (format "POST /ctia/%s/:id/expire revokes" entity-str)
                (let [fixed-now (-> "2020-12-31" tc/from-string tc/to-date)]
                  (helpers/fixture-with-fixed-time
                   fixed-now
                   (fn []
                     (let [response (apply helpers/POST
                                           app
                                           (format "ctia/%s/%s/expire" entity-str (:short-id record-id))
                                           :headers headers
                                           (when revoke-tests-extra-query-params
                                             [:query-params revoke-tests-extra-query-params]))]
                       (is (= 200 (:status response))
                           (format "POST %s/:id/expire succeeds" entity-str))
                       (is (= fixed-now (-> response :parsed-body :valid_time :end_time))
                           ":valid_time properly reset")))))))

            ;; execute entity custom tests before deleting the fixture
            (testing "additional tests"
              (when additional-tests
                (additional-tests app
                                  record-id
                                  (if update-tests?
                                    updated-record
                                    post-record))))))

        (when invalid-tests?
          (testing (format "PUT invalid /ctia/%s/:id" entity-str)
            (let [{status :status
                   body :body}
                  (PUT app
                       (format "ctia/%s/%s" entity-str (:short-id record-id))
                       :body (assoc post-record invalid-test-field (string/join
                                                                    (repeatedly 1025 (constantly \0))))
                       :headers {"Authorization" "45c1f5e3f05d0"})]
              (is (= status 400))
              (is (re-find (re-pattern
                            (str "error.*in.*"
                                 (name invalid-test-field)))
                           (string/lower-case body))))))

        (testing (format "DELETE non-existant /ctia/%s/:id" entity-str)
          (let [response (DELETE app
                                 (format "ctia/%s/%s-42424242" entity-str entity-str )
                                 :headers headers)]
            (is (= 404 (:status response)))))

        (testing (format "DELETE /ctia/%s/:id" entity-str)
          (let [response (DELETE app
                                 (format "ctia/%s/%s" entity-str (:short-id record-id))
                                 :headers headers)]
            (is (= 204 (:status response)))
            (let [response (GET app
                                (format "ctia/%s/%s" entity-str (:short-id record-id))
                                :headers headers)]
              (is (= 404 (:status response)))))))

      #_(when search-tests?
        (th.search/test-query-string-search
         {:app           app
          :entity        entity-str
          :query         (or search-value (name search-field))
          :query-field   search-field
          :example       example
          :get-in-config get-in-config}))
      (when delete-search-tests?
        (th.search/test-delete-search
         {:app        app
          :entity     entity-str
          :bundle-key (keyword (string/replace plural #"-" "_"))
          :example    example}))
      (when invalid-tests?
        (testing (format "POST invalid /ctia/%s :schema_version should be ignored" entity-str)
          (let [{status :status
                 record :parsed-body}
                (POST app
                      (str "ctia/" entity-str)
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
                    (PUT app
                         (format "ctia/%s/%s" entity-str (:short-id id))
                         ;; This update has an outdated schema_version
                         :body (-> record
                                   (assoc :schema_version "0.4.2"))
                         :headers headers)]
                (is (= status 200))
                (is (= (:schema_version updated-record)
                       schema-version))))))

        (testing (format "POST invalid /ctia/%s" entity-str)
          (let [{status :status
                 body :body}
                (POST app
                      (str "ctia/" entity-str)
                      ;; This field has an invalid length
                      :body (assoc example
                                   invalid-test-field (string/join (repeatedly 1025 (constantly \0))))
                      :headers headers)]
            (is (= status 400))
            (is (re-find (re-pattern
                          (str "error.*in.*" (name invalid-test-field)))
                         (string/lower-case body)))))))))
