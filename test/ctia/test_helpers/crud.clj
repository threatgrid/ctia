(ns ctia.test-helpers.crud
  (:refer-clojure :exclude [get])
  (:require [cheshire.core :refer [parse-string]]
            [clj-http.fake :refer [with-global-fake-routes]]
            [clj-momo.test-helpers.http :refer [encode]]
            [clojure
             [string :as string]
             [test :refer [is testing]]]
            [clojure.java.io :as io]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show properties]]
            [ctia.test-helpers
             [core :as helpers
              :refer [delete entity->short-id get patch post put]]
             [search :refer [test-query-string-search]]]
            [ctim.domain.id :as id]))

(defn crud-wait-for-test
  [{:keys [entity
           example
           headers
           update-field
           update-tests?
           patch-tests?]
    :or {update-field :title
         update-tests? true
         patch-tests? false}}]
  (let [new-record (dissoc example :id)
        default-es-refresh (let [d (get-in @properties
                                           [:ctia :store :es :default :refresh])]
                             (assert (string? d) (pr-str d))
                             (str "refresh=" d))
        simple-handler (fn [es-params]
                         {:pre [(instance? clojure.lang.IAtom es-params)]}
                         (fn [{:keys [query-string]}]
                           (swap! es-params (fn [old]
                                              {:pre [(nil? old)]
                                               :post [(string? %)]}
                                              query-string))
                           {:status 200
                            :headers {"Content-Type" "application/json"}
                            :body "{}"}))
        bulk-routes (fn [es-params]
                      {:pre [(instance? clojure.lang.IAtom es-params)]}
                      {#".*_bulk.*"
                       {:post (fn [{:keys [query-string body]}]
                                (let [mapping-type (-> (io/reader body)
                                                       line-seq
                                                       first
                                                       (parse-string true)
                                                       (get-in [:index :_type]))]
                                  (when-not (= "event" mapping-type)
                                    (swap! es-params (fn [old]
                                                       {:pre [(nil? old)]
                                                        :post [(string? %)]}
                                                       query-string)))
                                  {:status 200
                                   :headers {"Content-Type" "application/json"}
                                   :body "{}"}))}})
        check-refresh (fn [es-params-val wait_for msg]
                        {:pre [(not (instance? clojure.lang.IAtom es-params-val))]}
                        (let [expected (cond
                                         (nil? wait_for) default-es-refresh
                                         (true? wait_for) "refresh=wait_for"
                                         (false? wait_for) "refresh=false"
                                         :else (throw (ex-info "Bad wait_for"
                                                               {:wait_for wait_for})))]
                          (is (some-> es-params-val
                                      (string/includes? expected))
                              (str msg (format " (%s|%s)" expected es-params-val)))))]

    (testing "testing wait_for values on entity creation"
      (let [test-create (fn [wait_for msg]
                          (let [path (cond-> (str "ctia/" entity)
                                       (boolean? wait_for) (str "?wait_for=" wait_for))
                                es-params (atom nil)]
                            (with-global-fake-routes (bulk-routes es-params)
                              (post path
                                    :body new-record
                                    :headers headers))
                            (check-refresh @es-params wait_for msg)))]
        (test-create true
                     "Create queries should wait for index refresh when wait_for is true")
        (test-create false
                     (str "Create queries should not wait for index refresh when "
                          "wait_for is false"))
        (test-create nil
                     (str "Configured ctia.store.es.default.refresh value is applied "
                          "when wait_for is not specified for create queries"))))

    (testing "testing wait_for values on entity update / patch"
      (let [entity-id (-> (format "ctia/%s?wait_for=true" entity)
                          (post :body new-record
                                :headers headers)
                          :parsed-body
                          entity->short-id)
            test-modify (fn [method-kw wait_for msg]
                          (let [path (cond-> (format "ctia/%s/%s" entity entity-id)
                                       (boolean? wait_for) (str "?wait_for=" wait_for))
                                updates (cond->> {update-field "modified"}
                                          (= :put method-kw) (into new-record))
                                es-params (atom nil)]
                            (with-global-fake-routes {#".*9200.*" {:put (simple-handler es-params)}}
                              (let [method (case method-kw
                                             :put put
                                             :patch patch)]
                                (method path
                                        :body updates
                                        :headers headers)))
                            (check-refresh @es-params wait_for msg)))]
        (when update-tests?
          (test-modify :put true
                       (str "Update queries should wait for index refresh when "
                            "wait_for is true"))
          (test-modify :put false
                       (str "Update queries should not wait for index refresh "
                            "when wait_for is false"))
          (test-modify :put nil
                       (str "Configured ctia.store.es.default.refresh value "
                            "is applied when wait_for is not specified for update queries")))
        (when patch-tests?
          (test-modify :patch true
                       (str "Patch queries should wait for index refresh when "
                            "wait_for is true"))
          (test-modify :patch false
                       (str "Patch queries should not wait for index refresh "
                            "when wait_for is false"))
          (test-modify :patch nil
                       (str "Configured ctia.store.es.default.refresh value is "
                            "applied when wait_for is not specified for patch queries")))))

    (testing "testing wait_for values on entity deletion"
      (let [test-delete (fn [wait_for msg]
                          (let [entity-id (-> (post (str "ctia/" entity "?wait_for=true")
                                                    :body new-record
                                                    :headers headers)
                                              :parsed-body
                                              entity->short-id)
                                path (cond-> (format "ctia/%s/%s" entity entity-id)
                                       (boolean? wait_for) (str "?wait_for=" wait_for))
                                es-params (atom nil)]
                            (with-global-fake-routes {#".*9200.*" {:delete (simple-handler es-params)}}
                              (delete path
                                      :headers headers))
                            (check-refresh @es-params wait_for msg)))]
        (test-delete true
                     (str "Delete queries should wait for index refresh when "
                          "wait_for is true"))
        (test-delete false
                     (str "Delete queries should not wait for index refresh "
                          "when wait_for is false"))
        (test-delete nil
                     (str "Configured ctia.store.es.default.refresh value is "
                          "applied when wait_for is not specified for delete queries"))))))

(defn entity-crud-test
  [{:keys [entity
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
           additional-tests]
    :or {invalid-tests? true
         invalid-test-field :title
         update-field :title
         search-field :description
         optional-field :external_ids
         update-tests? true
         patch-tests? false
         search-tests? true}
    :as params}]
  (testing (str "POST /ctia/" entity)
    (let [new-record (dissoc example :id)
          {post-status :status
           post-record :parsed-body}
          (post (str "ctia/" entity "?wait_for=true")
                :body new-record
                :headers headers)
          record-id (id/long-id->id (:id post-record))
          expected (assoc post-record :id (id/long-id record-id))
          record-external-ids (:external_ids post-record)]
      (is (= 201 post-status))
      (is (= expected post-record))

      (testing (format "the %s ID has correct fields" entity)
        (let [show-props (get-http-show)]
          (is (= (:hostname record-id)    (:hostname show-props)))
          (is (= (:protocol record-id)    (:protocol show-props)))
          (is (= (:port record-id)        (:port show-props)))
          (is (= (:path-prefix record-id) (seq (:path-prefix show-props))))))

      (testing (format "GET /ctia/%s/:id" entity)
        (let [{get-status :status
               get-record :parsed-body}
              (get (format "ctia/%s/%s" entity (:short-id record-id))
                   :headers headers)]
          (is (= 200 get-status))
          (is (= expected get-record))))

      (when search-tests?
        (test-query-string-search entity
                                  (name search-field)
                                  search-field
                                  example))

      (testing (format "GET /ctia/%s/external_id/:external_id" entity)
        (let [response (get (format "ctia/%s/external_id/%s"
                                    entity (encode (rand-nth record-external-ids)))
                            :headers headers)
              records (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= [expected] records))))

      (testing (format "PATCH /ctia/%s/:id" entity)
        (let [updates {update-field "patch"}
              response (patch (format "ctia/%s/%s" entity (:short-id record-id))
                              :body updates
                              :headers headers)
              updated-record (:parsed-body response)]
          (when patch-tests?
            (is (= 200 (:status response)))
            (is (= (merge post-record updates)
                   updated-record)))))

      (testing (format "PUT /ctia/%s/:id" entity)
        (let [with-updates (-> post-record
                               (assoc update-field "modified")
                               (dissoc optional-field))
              {updated-record :parsed-body
               update-status :status}
              (put (format "ctia/%s/%s" entity (:short-id record-id))
                   :body with-updates
                   :headers headers)
              {stored-record :parsed-body}
              (get (format "ctia/%s/%s" entity (:short-id record-id))
                   :headers headers)]
          (when update-tests?
            (is (= 200 update-status))
            (is (= with-updates
                   updated-record))
            (is (= updated-record
                   stored-record)))

          ;; execute entity custom tests before deleting the fixture
          (testing "additional tests"
            (when additional-tests
              (additional-tests record-id
                                (if update-tests?
                                  updated-record
                                  post-record))))))

      (when invalid-tests?
        (testing (format "PUT invalid /ctia/%s/:id" entity)
          (let [{status :status
                 body :body}
                (put (format "ctia/%s/%s" entity (:short-id record-id))
                     :body (assoc post-record invalid-test-field (string/join
                                                                  (repeatedly 1025 (constantly \0))))
                     :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= status 400))
            (is (re-find (re-pattern
                          (str "error.*in.*"
                               (name invalid-test-field)))
                         (string/lower-case body))))))

      (testing (format "DELETE non-existent /ctia/%s/:id" entity)
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
                                 invalid-test-field (string/join (repeatedly 1025 (constantly \0))))
                    :headers headers)]
          (is (= status 400))
          (is (re-find (re-pattern
                        (str "error.*in.*" (name invalid-test-field)))
                       (string/lower-case body))))))

    (when (= "es"
             (get-in @properties
                     [:ctia :store (keyword entity)]))
      (crud-wait-for-test params))))
