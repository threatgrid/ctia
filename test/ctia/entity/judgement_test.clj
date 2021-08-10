(ns ctia.entity.judgement-test
  (:require [clj-momo.lib.clj-time.coerce :as tc]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.entity.judgement :as sut]
            [ctia.entity.judgement.schemas
             :refer
             [judgement-enumerable-fields
              judgement-histogram-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [GET POST]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [schema.test :refer [validate-schemas]]
            [ctim.examples.judgements :as ex]))

(use-fixtures :once (join-fixtures [validate-schemas
                                    helpers/fixture-properties:cors
                                    whoami-helpers/fixture-server]))

(def new-judgement
  (merge ex/new-judgement-maximal
         {:observable {:value "1.2.3.4"
                       :type "ip"}
          :external_ids ["http://ex.tld/ctia/judgement/judgement-123"
                         "http://ex.tld/ctia/judgement/judgement-456"]
          :disposition 2
          :disposition_name "Malicious"
          :source "test"
          :priority 100
          :severity "High"
          :confidence "Low"
          :reason "This is a bad IP address that talked to some evil servers"}))

(defn additional-tests [app judgement-id _]
  (testing "GET /ctia/judgement/search"
   (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
    ;; only when ES store
    (when (= "es" (get-in-config [:ctia :store :indicator]))
      (let [term "observable.value:\"1.2.3.4\""
            response (GET app
                          (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "IP quoted term works"))

      ;; TODO: this test no longer works, because apparently, in order to
      ;; perform search for values within nested fields (without Lucene
      ;; syntax), we're going to have to implement a way po pass nested fields, e.g.,:
      ;; "query_string": {"query": "1.2.3.4", "fields": ["observable.*^1.0"] - note the asterisk
      #_(let [term     "1.2.3.4"
              response (GET app
                           (str "ctia/judgement/search")
                         :headers {"Authorization" "45c1f5e3f05d0"}
                         :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "IP unquoted, all term works"))

      (let [term "Evil Servers"
            response (GET app
                          (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "Full text search, mixed case, _all term works"))

      (let [term "disposition_name:Malicious"
            response (GET app
                          (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "uppercase term works"))

      (let [term "disposition_name:malicious"
            response (GET app
                          (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "lowercase quoted term works"))

      (let [term "disposition_name:Malicious"
            response (GET app
                          (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term
                                         "tlp" "red"})]
        (is (= 200 (:status response)))
        (is (empty? (:parsed-body response))
            "filters are applied, and discriminate"))

      (let [term "disposition_name:Malicious"
            response (GET app
                          (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term
                                         "tlp" "green"})]
        (is (= 200 (:status response)))
        (is (= 1  (count (:parsed-body response)))
            "filters are applied, and match properly")))))

  (testing "GET /ctia/judgement/:id authentication failures"
    (testing "no Authorization"
      (let [{body :parsed-body status :status}
            (GET app
                 (str "ctia/judgement/" (:short-id judgement-id)))]
        (is (= 401 status))
        (is (= {:message "Only authenticated users allowed"
                :error :not_authenticated}
               body))))

    (testing "unknown Authorization"
      (let [{body :parsed-body status :status}
            (GET app
                 (str "ctia/judgement/" (:short-id judgement-id))
                 :headers {"Authorization" "1111111111111"})]
        (is (= 401 status))
        (is (= {:message "Only authenticated users allowed"
                :error :not_authenticated}
               body))))

    (testing "doesn't have read capability"
      (let [{body :parsed-body status :status}
            (GET app
                 (str "ctia/judgement/" (:short-id judgement-id))
                 :headers {"Authorization" "2222222222222"})]
        (is (= 403 status))
        (is (= {:message "Missing capability",
                :capabilities :read-judgement,
                :owner "baruser"
                :error :missing_capability}
               body)))))
  (testing "POST /ctia/judgement/:id/expire revokes"
    (let [fixed-now (-> "2020-12-31" tc/from-string tc/to-date)]
      (helpers/fixture-with-fixed-time
        fixed-now
        (fn []
          (let [expiry-reason "(because it's old)"
                {{:keys [^String reason
                         valid_time]}
                 :parsed-body :as response}
                (POST
                  app
                  (format "ctia/judgement/%s/expire"
                          (:short-id judgement-id))
                  :query-params {"reason" expiry-reason}
                  :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 200 (:status response))
                "POST ctia/judgement/:id/expire succeeds")
            (is (= fixed-now (:end_time valid_time))
                ":valid_time correctly reset")
            (is (.endsWith reason (str " " expiry-reason))
                (str ":reason correctly appended: " (pr-str reason))))))))
  (testing "POST /ctia/judgement/:id/expire requires reason"
    (let [fixed-now (-> "2020-12-31" tc/from-string tc/to-date)]
      (helpers/fixture-with-fixed-time
        fixed-now
        (fn []
          (let [response
                (POST
                  app
                  (format "ctia/judgement/%s/expire"
                          (:short-id judgement-id))
                  :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 400 (:status response))
                "POST ctia/judgement/:id/expire succeeds")))))))

(deftest test-judgement-crud-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroupi"] "user" all-capabilities)
     (helpers/set-capabilities! app "baruser"  ["bargroup"] "user" #{})
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (whoami-helpers/set-whoami-response app
                                         "2222222222222"
                                         "baruser"
                                         "bargroup"
                                         "user")

     (entity-crud-test
      (into sut/judgement-entity
            {:app app
             :example new-judgement
             :update-tests? true
             :update-field :source
             :invalid-tests? false
             :search-tests? false
             :additional-tests additional-tests
             :revoke-tests? true
             :revoke-tests-extra-query-params {"reason" "(some reason)"}
             :headers {:Authorization "45c1f5e3f05d0"}})))))

(deftest test-judgement-metric-routes
  (test-metric-routes (into sut/judgement-entity
                            {:entity-minimal ex/new-judgement-minimal
                             :enumerable-fields judgement-enumerable-fields
                             :date-fields judgement-histogram-fields})))

(deftest test-judgement-routes-for-dispositon-determination
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "POST a judgement with dispositon (id)"
       (let [{status :status
              judgement :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :disposition 2
                          :source "test"
                          :priority 100
                          :timestamp #inst "2042-01-01T00:00:00.000Z"
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))
         (is (=
              {:type "judgement"
               :observable {:value "1.2.3.4"
                            :type "ip"}
               :disposition 2
               :disposition_name "Malicious"
               :source "test"
               :priority 100
               :timestamp #inst "2042-01-01T00:00:00.000Z"
               :severity "High"
               :confidence "Low"
               :tlp "green"
               :schema_version schema-version
               :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                            :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
              (dissoc judgement
                      :id
                      :groups ["foogroup"]
                      :owner "foouser")))))

     (testing "POST a judgement with disposition_name"
       (let [{status :status
              judgement :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :disposition_name "Malicious"
                          :source "test"
                          :priority 100
                          :timestamp #inst "2042-01-01T00:00:00.000Z"
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))
         (is (= {:type "judgement"
                 :observable {:value "1.2.3.4"
                              :type "ip"}
                 :disposition 2
                 :disposition_name "Malicious"
                 :source "test"
                 :priority 100
                 :timestamp #inst "2042-01-01T00:00:00.000Z"
                 :severity "High"
                 :confidence "Low"
                 :tlp "green"
                 :schema_version schema-version
                 :owner "foouser"
                 :groups ["foogroup"]
                 :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                              :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
                (dissoc judgement
                        :id)))))

     (testing "POST a judgement without disposition"
       (let [{status :status
              judgement :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :source "test"
                          :priority 100
                          :timestamp #inst "2042-01-01T00:00:00.000Z"
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))
         (is (=
              {:type "judgement"
               :observable {:value "1.2.3.4"
                            :type "ip"}
               :disposition 5
               :disposition_name "Unknown"
               :source "test"
               :priority 100
               :timestamp #inst "2042-01-01T00:00:00.000Z"
               :severity "High"
               :confidence "Low"
               :tlp "green"
               :owner "foouser"
               :groups ["foogroup"]
               :schema_version schema-version
               :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                            :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
              (dissoc judgement
                      :id)))))

     (testing "POST a judgement with mismatched disposition/disposition_name"
       (let [{status :status
              judgement :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :disposition 1
                          :disposition_name "Unknown"
                          :source "test"
                          :priority 100
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 400 status))
         (is (=
              {:error "Mismatched disposition and dispositon_name for judgement",
               :judgement {:observable {:value "1.2.3.4"
                                        :type "ip"}
                           :disposition 1
                           :disposition_name "Unknown"
                           :source "test"
                           :priority 100
                           :severity "High"
                           :confidence "Low"
                           :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}}}
              judgement))))

     (testing "POST a judgement with mismatched disposition/disposition_name"
       (let [{status :status
              judgement :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :disposition 1
                          :disposition_name "Unknown"
                          :source "test"
                          :priority 100
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 400 status))
         (is (= {:error "Mismatched disposition and dispositon_name for judgement",
                 :judgement {:observable {:value "1.2.3.4"
                                          :type "ip"}
                             :disposition 1
                             :disposition_name "Unknown"
                             :source "test"
                             :priority 100
                             :severity "High"
                             :confidence "Low"
                             :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}}}
                judgement)))))))

(deftest test-judgement-routes-access-control
  (access-control-test "judgement"
                       ex/new-judgement-minimal
                       true
                       true
                       test-for-each-store-with-app))
