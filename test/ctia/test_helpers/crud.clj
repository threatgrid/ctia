(ns ctia.test-helpers.crud
  (:refer-clojure :exclude [run!])
  (:require [cheshire.core :refer [parse-string]]
            [clj-http.fake :refer [with-global-fake-routes]]
            [clj-momo.lib.clj-time.coerce :as tc]
            [clj-momo.test-helpers.http :refer [encode]]
            [clojure
             [string :as string]
             [test :refer [is testing]]]
            [clojure.java.io :as io]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :as p :refer [get-http-show]]
            [ctia.lib.utils :refer [run!]]
            [ctia.test-helpers
             [core :as helpers
              :refer [DELETE entity->short-id GET PATCH POST PUT]]
             [http :refer [app->HTTPShowServices]]
             [search :refer [test-query-string-search]]]
            [ctim.domain.id :as id]
            [schema.core :as s]))

(s/defschema WaitForTestStep
  {:method-kw (s/pred #{:DELETE
                        :PATCH
                        :POST
                        :PUT
                        :revoke})
   :wait_for (s/pred (some-fn nil?
                              boolean?))})

(s/defn gen-wait-for-test-plan :- [WaitForTestStep]
  "Generate a test plan for testing wait_for for any entity."
  [{:keys [update-tests?
           patch-tests?
           revoke-tests?]
    :or {update-tests? true}}]
  {:post [(seq %)]}
  (->> [:POST
        :DELETE
        (when update-tests? :PUT)
        (when patch-tests? :PATCH)
        (when revoke-tests? :revoke)]
       (mapcat (s/fn :- [WaitForTestStep]
                 [method-kw]
                 (when method-kw
                   (for [wait_for #{true false nil}]
                     {:method-kw method-kw
                      :wait_for wait_for}))))
       ;; sort so we can have meaningful test.check seeds
       (sort-by (juxt :method-kw :wait_for))))

(comment
  (gen-wait-for-test-plan {})
  (gen-wait-for-test-plan {:update-tests? true})
  (gen-wait-for-test-plan {:update-tests? false})
  (gen-wait-for-test-plan {:patch-tests? true})
  (gen-wait-for-test-plan {:patch-tests? true
                           :update-tests? true})
  )

(s/defn crud-wait-for-test
  "Tests the wait_for query param for the given entity.
  
  (crud-wait-for-test params)
     Runs all wait_for tests for specified entity.

  (crud-wait-for-test params test-step)
     Runs just the tests specified by test-step (see WaitForTestStep) for
     the specified entity.
     eg., (crud-wait-for-test {..
                               :app ..
                               :entity :asset-mapping
                               :crud-wait_for-quickcheck-options
                               {:num-tests 100
                                :seed 1605287900915}
                               ..}
                              {:method-kw :POST,
                               :wait_for true})"
  ([params]
   (let [params (into {:update-tests? true
                       :patch-tests? false}
                      params)
         qc-opts (-> (:crud-wait_for-quickcheck-options params)
                     (update :num-tests #(or % 2)))
         test-plan-generator (-> (gen-wait-for-test-plan params)
                                 gen/shuffle 
                                 gen/no-shrink)]
     (checking "wait_for operations" qc-opts
       [t test-plan-generator]
       (run! (partial crud-wait-for-test params)
             t))))
  ([params
    test-step :- WaitForTestStep]
   (testing (str "\n" test-step)
     (let [{:keys [app
                   entity
                   example
                   headers
                   update-field]
            :as params
            :or {update-field :title}} params
           {:keys [method-kw wait_for]} test-step
           get-in-config (helpers/current-get-in-config-fn app)
           default-es-refresh (->> (get-in-config
                                     [:ctia :store :es :default :refresh])
                                   (str "refresh="))
           empty-es-params ::unset-es-params
           es-params (atom empty-es-params)
           reset-es-params! (fn [query-string]
                              (swap! es-params (fn [old]
                                                 (assert (identical? old empty-es-params)
                                                         "Overwriting es-params!")
                                                 query-string)))
           check-refresh (fn []
                           (let [msg (case wait_for
                                       true (str method-kw " queries should wait for index refresh when "
                                                 "wait_for is true")
                                       false (str method-kw " queries should not wait for index refresh "
                                                  "when wait_for is false")
                                       nil (str "Configured ctia.store.es.default.refresh value "
                                                "is applied when wait_for is not specified"))
                                 expected (cond
                                            (nil? wait_for) default-es-refresh
                                            (true? wait_for) "refresh=wait_for"
                                            (false? wait_for) "refresh=false")
                                 actual-es-params @es-params]
                             (is (not= empty-es-params actual-es-params)
                                 "Route was not called")
                             (is (some-> actual-es-params
                                         (string/includes? expected))
                                 (format "%s (expected %s, actual: %s)" msg expected actual-es-params))))
           new-record (dissoc example :id)
           entity-id (-> (POST app
                               (format "ctia/%s?wait_for=true" entity)
                               :body new-record
                               :headers headers)
                         :parsed-body
                         entity->short-id)
           fake-routes (case method-kw
                         :POST (let [bulk-routes {#".*_bulk.*"
                                                  {:post (fn [{:keys [query-string body]}]
                                                           (let [mapping-type (-> (io/reader body)
                                                                                  line-seq
                                                                                  first
                                                                                  (parse-string true)
                                                                                  (get-in [:index :_type]))]
                                                             (when-not (= "event" mapping-type)
                                                               (reset-es-params! query-string))
                                                             {:status 200
                                                              :headers {"Content-Type" "application/json"}
                                                              :body "{}"}))}}]
                                 bulk-routes)
                         #_:else
                         (let [es-index-uri-pattern (re-pattern (str ".*9200.*" entity-id ".*"))
                               simple-handler (fn [{:keys [url query-string]}]
                                                (reset-es-params! query-string)
                                                {:status 200
                                                 :headers {"Content-Type" "application/json"}
                                                 :body "{}"})]
                           {es-index-uri-pattern
                            {(case method-kw
                               :DELETE :delete
                               :put)
                             simple-handler}}))
           path (cond-> (format "ctia/%s" entity)
                  ;; all routes except :POST use /:id
                  (not (#{:POST} method-kw)) (str "/" entity-id)
                  ;; revocation uses an extra /expire path
                  (#{:revoke} method-kw) (str "/expire")
                  (boolean? wait_for) (str "?wait_for=" wait_for))
           body (case method-kw
                  ;; only put and patch need bodies
                  (:PUT :PATCH) (cond->> {update-field "modified"}
                                  (= :PUT method-kw) (into new-record))
                  nil)]
       (with-global-fake-routes fake-routes
         (let [;; down here to prevent accidents
               method (case method-kw
                        :DELETE DELETE
                        :POST POST
                        :PUT PUT
                        :PATCH PATCH
                        :revoke POST)]
           (apply method app
                  path
                  :headers headers
                  (when body
                    [:body body]))))
       (check-refresh)))))

(defn entity-crud-test
 [params]
 (let [{:keys [app
               entity
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
               additional-tests
               search-value
               revoke-tests?]
        :as params} (into
                      {:invalid-tests? true
                       :invalid-test-field :title
                       :update-field :title
                       :search-field :description
                       :optional-field :external_ids
                       :update-tests? true
                       :patch-tests? false
                       :search-tests? true}
                      params)
       get-in-config (helpers/current-get-in-config-fn app)]
  (testing (str "POST /ctia/" entity)
    (let [new-record (dissoc example :id)
          {post-status :status
           post-record :parsed-body}
          (POST app
                (str "ctia/" entity "?wait_for=true")
                :body new-record
                :headers headers)
          record-id (id/long-id->id (:id post-record))
          expected (assoc post-record :id (id/long-id record-id))
          record-external-ids (:external_ids post-record)]
      (is (= 201 post-status))
      (is (= expected post-record))

      (testing (format "the %s ID has correct fields" entity)
        (let [show-props (get-http-show (app->HTTPShowServices app))]
          (is (= (:hostname record-id)    (:hostname show-props)))
          (is (= (:protocol record-id)    (:protocol show-props)))
          (is (= (:port record-id)        (:port show-props)))
          (is (= (:path-prefix record-id) (seq (:path-prefix show-props))))))

      (testing (format "GET /ctia/%s/:id" entity)
        (let [{get-status :status
               get-record :parsed-body}
              (GET app
                   (format "ctia/%s/%s" entity (:short-id record-id))
                   :headers headers)]
          (is (= 200 get-status))
          (is (= expected get-record))))

      (when search-tests?
        (test-query-string-search app
                                  entity
                                  (or search-value
                                      (name search-field))
                                  search-field
                                  example
                                  get-in-config))

      (testing (format "GET /ctia/%s/external_id/:external_id" entity)
        (let [response (GET app
                            (format "ctia/%s/external_id/%s"
                                    entity (encode (rand-nth record-external-ids)))
                            :headers headers)
              records (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= [expected] records))))

      (testing (format "PATCH /ctia/%s/:id" entity)
        (let [updates {update-field "patch"}
              response (PATCH app
                              (format "ctia/%s/%s" entity (:short-id record-id))
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
              (PUT app
                   (format "ctia/%s/%s" entity (:short-id record-id))
                   :body with-updates
                   :headers headers)
              {stored-record :parsed-body}
              (GET app
                   (format "ctia/%s/%s" entity (:short-id record-id))
                   :headers headers)]
          (when update-tests?
            (is (= 200 update-status))
            (is (= with-updates
                   updated-record))
            (is (= updated-record
                   stored-record)))

          ;; TODO wait_for query param?
          (when revoke-tests?
            (testing (format "POST /ctia/%s/:id/expire revokes" entity)
              (let [fixed-now (-> "2020-12-31" tc/from-string tc/to-date)]
                (helpers/fixture-with-fixed-time
                  fixed-now
                  (fn []
                    (let [response (helpers/POST
                                     app
                                     (format "ctia/%s/%s/expire" entity (:short-id record-id))
                                     :headers headers)]
                      (is (= 200 (:status response))
                          (format "POST %s/:id/expire succeeds" entity))
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
        (testing (format "PUT invalid /ctia/%s/:id" entity)
          (let [{status :status
                 body :body}
                (PUT app
                     (format "ctia/%s/%s" entity (:short-id record-id))
                     :body (assoc post-record invalid-test-field (string/join
                                                                  (repeatedly 1025 (constantly \0))))
                     :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= status 400))
            (is (re-find (re-pattern
                          (str "error.*in.*"
                               (name invalid-test-field)))
                         (string/lower-case body))))))

      (testing (format "DELETE non-existant /ctia/%s/:id" entity)
        (let [response (DELETE app
                               (format "ctia/%s/%s-42424242" entity entity )
                               :headers headers)]
          (is (= 404 (:status response)))))

      (testing (format "DELETE /ctia/%s/:id" entity)
        (let [response (DELETE app
                               (format "ctia/%s/%s" entity (:short-id record-id))
                               :headers headers)]
          (is (= 204 (:status response)))
          (let [response (GET app
                              (format "ctia/%s/%s" entity (:short-id record-id))
                              :headers headers)]
            (is (= 404 (:status response)))))))

    (when invalid-tests?
      (testing (format "POST invalid /ctia/%s :schema_version should be ignored" entity)
        (let [{status :status
               record :parsed-body}
              (POST app
                    (str "ctia/" entity)
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
                       (format "ctia/%s/%s" entity (:short-id id))
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
              (POST app
                    (str "ctia/" entity)
                    ;; This field has an invalid length
                    :body (assoc example
                                 invalid-test-field (string/join (repeatedly 1025 (constantly \0))))
                    :headers headers)]
          (is (= status 400))
          (is (re-find (re-pattern
                        (str "error.*in.*" (name invalid-test-field)))
                       (string/lower-case body))))))

    (when (= "es"
             (get-in-config
               [:ctia :store (keyword entity)]))
      (crud-wait-for-test params)))))
