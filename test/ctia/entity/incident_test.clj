(ns ctia.entity.incident-test
  (:require [cemerick.uri :as uri]
            [clj-momo.lib.clj-time.coerce :as tc]
            [clj-momo.lib.clj-time.core :as t]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.gfredericks.test.chuck.generators :as gen']
            [ctia.auth.threatgrid :as auth]
            [ctia.bundle.core :as bundle]
            [ctia.entity.incident :as sut]
            [ctia.test-helpers.access-control :refer [access-control-test]]
            [ctia.test-helpers.aggregate :refer [test-metric-routes]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers :refer [GET POST PATCH POST]]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.search :as search-th]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.domain.id :as id]
            [ctim.examples.bundles :refer [new-bundle-minimal]]
            [ctim.examples.incidents
             :refer
             [new-incident-maximal new-incident-minimal incident-minimal]]
            ductile.index
            [java-time.api :as jt]
            [puppetlabs.trapperkeeper.app :as app]
            [schema-tools.core :as st]
            [schema.core :as s]
            [clojure.string :as string]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(deftest incident-scores-schema-test
  (let [get-in-config (partial get-in {:ctia {:http {:incident {:score-types "global,ttp,asset"}}}})
        fake-services {:ConfigService {:get-in-config get-in-config}}
        expected (st/optional-keys {:global s/Num :ttp s/Num :asset s/Num})
        res (sut/mk-scores-schema fake-services)]
    (is (= expected res))
    (is (= {} (sut/mk-scores-schema {:ConfigService {:get-in-config (constantly nil)}})))))

(defn post-status
  [app uri-encoded-id new-status]
  (POST app
        (str "ctia/incident/" uri-encoded-id "/status")
        :body {:status new-status}
        :headers {"Authorization" "45c1f5e3f05d0"
                  "wait_for" true}))

(defn get-incident [app id]
  (GET app (str "ctia/incident/" (uri/uri-encode id))
       :headers {"Authorization" "45c1f5e3f05d0"}))

(defn create-test-incident
  "Creates a fresh incident with status 'New' for testing. Returns the incident ID."
  [app]
  (let [incident-to-create (-> new-incident-minimal
                                (dissoc :id :severity :incident_time)
                                (assoc :title (str "Test Incident " (java.util.UUID/randomUUID))
                                       :status "New"
                                       ;; incident_time is required and must have :opened
                                       :incident_time {:opened (t/internal-now)}))
        response (POST app
                       "ctia/incident"
                       :body incident-to-create
                       :headers {"Authorization" "45c1f5e3f05d0"})
        incident (:parsed-body response)]
    (assert (= 201 (:status response)) (str "Failed to create incident: " response))
    (:id incident)))

(defn delete-incident
  "Deletes an incident by ID."
  [app incident-id]
  (helpers/DELETE app
                  (str "ctia/incident/" (uri/uri-encode incident-id))
                  :headers {"Authorization" "45c1f5e3f05d0"}))

(defn additional-tests [app incident-id incident]
  (let [fixed-now (t/internal-now)
        ;; Track test incidents for cleanup
        test-incidents (atom [])]
    (try
      (helpers/fixture-with-fixed-time
       fixed-now
       (fn []
       ;; Keep the original POST status tests with the shared incident
       (testing "Incident status update: test setup"
         (let [response (PATCH app
                               (str "ctia/incident/" (:short-id incident-id))
                               :body {:incident_time {}}
                               :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 200 (:status response)))))

       (testing "POST /ctia/incident/:id/status Open"
         (let [new-status "Open"
               response (post-status app (:short-id incident-id) new-status)
               updated-incident (:parsed-body response)]
           (is (= (:id incident) (:id updated-incident)))
           (is (= 200 (:status response)))
           (is (= "Open" (:status updated-incident)))
           (is (= (get-in updated-incident [:incident_time :opened])
                  (tc/to-date fixed-now)))))

       (testing "POST /ctia/incident/:id/status Closed"
         (let [new-status "Closed"
               response (post-status app (:short-id incident-id) new-status)
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Closed" (:status updated-incident)))
           (is (= (get-in updated-incident [:incident_time :closed])
                  (tc/to-date fixed-now)))))

       (testing "POST /ctia/incident/:id/status Containment Achieved"
         (let [new-status "Containment Achieved"
               response (post-status app (:short-id incident-id) new-status)
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Containment Achieved" (:status updated-incident)))
           (is (= (get-in updated-incident [:incident_time :remediated])
                  (tc/to-date fixed-now)))))
       
       (testing "POST /ctia/incident/:id/status Contained"
         (let [new-status "Open: Contained"
               response (post-status app (:short-id incident-id) new-status)
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Open: Contained" (:status updated-incident)))
           (is (= (get-in updated-incident [:incident_time :contained])
                  (tc/to-date fixed-now)))))

       ;; PATCH tests with fresh incidents for each test
       (testing "PATCH /ctia/incident/:id with status change to Open"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               response (PATCH app
                               (str "ctia/incident/" (uri/uri-encode test-id))
                               :body {:status "Open"}
                               :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Open" (:status updated-incident)))
           (is (= (get-in updated-incident [:incident_time :opened])
                  (tc/to-date fixed-now)))))

       (testing "PATCH /ctia/incident/:id with status change to Closed"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               ;; First set to Open to have a valid transition
               _ (PATCH app
                        (str "ctia/incident/" (uri/uri-encode test-id))
                        :body {:status "Open"}
                        :headers {"Authorization" "45c1f5e3f05d0"})
               ;; Then test New -> Closed transition
               response (PATCH app
                               (str "ctia/incident/" (uri/uri-encode test-id))
                               :body {:status "Closed"}
                               :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Closed" (:status updated-incident)))
           (is (= (get-in updated-incident [:incident_time :closed])
                  (tc/to-date fixed-now)))))

       (testing "PATCH /ctia/incident/:id with status change to Containment Achieved"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               response (PATCH app
                               (str "ctia/incident/" (uri/uri-encode test-id))
                               :body {:status "Containment Achieved"}
                               :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Containment Achieved" (:status updated-incident)))
           (is (= (get-in updated-incident [:incident_time :remediated])
                  (tc/to-date fixed-now)))))

       (testing "PATCH /ctia/incident/:id with status change to Open: Contained"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               response (PATCH app
                               (str "ctia/incident/" (uri/uri-encode test-id))
                               :body {:status "Open: Contained"}
                               :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Open: Contained" (:status updated-incident)))
           (is (= (get-in updated-incident [:incident_time :contained])
                  (tc/to-date fixed-now)))
           (is (= (get-in updated-incident [:incident_time :opened])
                  (tc/to-date fixed-now)))))

       (testing "PATCH /ctia/incident/:id without status change does not update incident_time"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               ;; First, set a status to establish incident_time
               _ (PATCH app
                        (str "ctia/incident/" (uri/uri-encode test-id))
                        :body {:status "Open"}
                        :headers {"Authorization" "45c1f5e3f05d0"})
               ;; Get current incident
               current (get-incident app test-id)
               current-incident (:parsed-body current)
               current-incident-time (:incident_time current-incident)
               ;; Update something other than status
               response (PATCH app
                               (str "ctia/incident/" (uri/uri-encode test-id))
                               :body {:description "Updated description"}
                               :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Updated description" (:description updated-incident)))
           ;; incident_time should remain unchanged
           (is (= current-incident-time (:incident_time updated-incident)))))

       (testing "PATCH /ctia/incident/:id preserves explicitly set incident_time fields"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               custom-time (tc/to-date (t/plus fixed-now (t/hours 2)))
               response (PATCH app
                               (str "ctia/incident/" (uri/uri-encode test-id))
                               :body {:status "Open"
                                      :incident_time {:opened custom-time}}
                               :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Open" (:status updated-incident)))
           ;; Should use the explicitly provided time, not the generated one
           (is (= custom-time (get-in updated-incident [:incident_time :opened])))))

       (testing "POST /ctia/incident/:id/status Hold to Closed transition"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               ;; First set status to Hold
               hold-response (post-status app (uri/uri-encode test-id) "Hold")
               _ (is (= 200 (:status hold-response)))
               _ (is (= "Hold" (:status (:parsed-body hold-response))))
               ;; Then transition to Closed
               closed-response (post-status app (uri/uri-encode test-id) "Closed")
               updated-incident (:parsed-body closed-response)]
           (is (= 200 (:status closed-response)))
           (is (= "Closed" (:status updated-incident)))
           (is (= (get-in updated-incident [:incident_time :closed])
                  (tc/to-date fixed-now)))))

       (testing "PATCH /ctia/incident/:id with status change from Hold to Closed"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               ;; First set status to Hold
               hold-response (PATCH app
                                    (str "ctia/incident/" (uri/uri-encode test-id))
                                    :body {:status "Hold"}
                                    :headers {"Authorization" "45c1f5e3f05d0"})
               _ (is (= 200 (:status hold-response)))
               _ (is (= "Hold" (:status (:parsed-body hold-response))))
               ;; Then transition to Closed via PATCH
               closed-response (PATCH app
                                      (str "ctia/incident/" (uri/uri-encode test-id))
                                      :body {:status "Closed"}
                                      :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body closed-response)]
           (is (= 200 (:status closed-response)))
           (is (= "Closed" (:status updated-incident)))
           (is (= (get-in updated-incident [:incident_time :closed])
                  (tc/to-date fixed-now)))))

       (testing "PATCH /ctia/incident/:id with status change from Hold: Internal to Closed: Other"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               ;; First set status to Hold: Internal
               hold-response (PATCH app
                                    (str "ctia/incident/" (uri/uri-encode test-id))
                                    :body {:status "Hold: Internal"}
                                    :headers {"Authorization" "45c1f5e3f05d0"})
               _ (is (= 200 (:status hold-response)))
               _ (is (= "Hold: Internal" (:status (:parsed-body hold-response))))
               ;; Then transition to Closed: Other via PATCH
               closed-response (PATCH app
                                      (str "ctia/incident/" (uri/uri-encode test-id))
                                      :body {:status "Closed: Other"}
                                      :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body closed-response)]
           (is (= 200 (:status closed-response)))
           (is (= "Closed: Other" (:status updated-incident)))
           (is (= (get-in updated-incident [:incident_time :closed])
                  (tc/to-date fixed-now)))))))

       (testing "POST /ctia/incident/:id/status: Closed date preserved when transitioning between closed statuses"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               ;; First transition to closed status
               first-closed-response (post-status app (uri/uri-encode test-id) "Closed: False Positive")
               first-closed-incident (:parsed-body first-closed-response)
               first-closed-date (get-in first-closed-incident [:incident_time :closed])]
           (is (= 200 (:status first-closed-response)))
           (is (= "Closed: False Positive" (:status first-closed-incident)))
           (is (some? first-closed-date) "First closed date should be set")
           ;; Transition to another closed status with a later time
           (helpers/fixture-with-fixed-time
            (t/plus fixed-now (t/minutes 5))
            (fn []
              (let [second-closed-response (post-status app (uri/uri-encode test-id) "Closed: Confirmed Threat")
                    second-closed-incident (:parsed-body second-closed-response)
                    second-closed-date (get-in second-closed-incident [:incident_time :closed])]
                (is (= 200 (:status second-closed-response)))
                (is (= "Closed: Confirmed Threat" (:status second-closed-incident)))
                ;; The closed date should be preserved from the first closed status
                (is (= first-closed-date second-closed-date)
                    "Closed date should NOT be updated when transitioning between closed statuses"))))))

       (testing "PATCH /ctia/incident/:id: Closed date preserved when transitioning between closed statuses"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               ;; First transition to closed status via PATCH
               first-closed-response (PATCH app
                                           (str "ctia/incident/" (uri/uri-encode test-id))
                                           :body {:status "Closed: False Positive"}
                                           :headers {"Authorization" "45c1f5e3f05d0"})
               first-closed-incident (:parsed-body first-closed-response)
               first-closed-date (get-in first-closed-incident [:incident_time :closed])]
           (is (= 200 (:status first-closed-response)))
           (is (= "Closed: False Positive" (:status first-closed-incident)))
           (is (some? first-closed-date) "First closed date should be set")
           ;; Transition to another closed status with a later time via PATCH
           (helpers/fixture-with-fixed-time
            (t/plus fixed-now (t/minutes 5))
            (fn []
              (let [second-closed-response (PATCH app
                                                 (str "ctia/incident/" (uri/uri-encode test-id))
                                                 :body {:status "Closed: Confirmed Threat"}
                                                 :headers {"Authorization" "45c1f5e3f05d0"})
                    second-closed-incident (:parsed-body second-closed-response)
                    second-closed-date (get-in second-closed-incident [:incident_time :closed])]
                (is (= 200 (:status second-closed-response)))
                (is (= "Closed: Confirmed Threat" (:status second-closed-incident)))
                ;; The closed date should be preserved from the first closed status
                (is (= first-closed-date second-closed-date)
                    "Closed date should NOT be updated when transitioning between closed statuses"))))))

       (testing "POST /ctia/incident/:id/status: Opened date preserved when transitioning between open statuses"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               ;; First transition from New to Open
               first-open-response (post-status app (uri/uri-encode test-id) "Open")
               first-open-incident (:parsed-body first-open-response)
               initial-opened-date (get-in first-open-incident [:incident_time :opened])]

           (is (= 200 (:status first-open-response)))
           (is (= "Open" (:status first-open-incident)))
           (is (some? initial-opened-date) "Initial opened date should be set")

           ;; Transition to another open status with a later time
           (helpers/fixture-with-fixed-time
            (t/plus fixed-now (t/minutes 5))
            (fn []
              (let [response (post-status app (uri/uri-encode test-id) "Open: Investigating")
                    updated-incident (:parsed-body response)
                    new-opened-date (get-in updated-incident [:incident_time :opened])]

                (is (= 200 (:status response)))
                (is (= "Open: Investigating" (:status updated-incident)))
                (is (some? new-opened-date) "Opened date should still be set")

                ;; The opened date should be preserved from the first open status
                (is (= initial-opened-date new-opened-date)
                    "Opened date should NOT be updated when transitioning between open statuses"))))))

       (testing "PATCH /ctia/incident/:id: Opened date preserved when transitioning between open statuses"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               ;; First transition from New to Open via PATCH
               first-open-response (PATCH app
                                         (str "ctia/incident/" (uri/uri-encode test-id))
                                         :body {:status "Open"}
                                         :headers {"Authorization" "45c1f5e3f05d0"})
               first-open-incident (:parsed-body first-open-response)
               initial-opened-date (get-in first-open-incident [:incident_time :opened])]

           (is (= 200 (:status first-open-response)))
           (is (= "Open" (:status first-open-incident)))
           (is (some? initial-opened-date) "Initial opened date should be set")

           ;; Transition to another open status with a later time via PATCH
           (helpers/fixture-with-fixed-time
            (t/plus fixed-now (t/minutes 5))
            (fn []
              (let [response (PATCH app
                                   (str "ctia/incident/" (uri/uri-encode test-id))
                                   :body {:status "Open: Investigating"}
                                   :headers {"Authorization" "45c1f5e3f05d0"})
                    updated-incident (:parsed-body response)
                    new-opened-date (get-in updated-incident [:incident_time :opened])]

                (is (= 200 (:status response)))
                (is (= "Open: Investigating" (:status updated-incident)))
                (is (some? new-opened-date) "Opened date should still be set")

                ;; The opened date should be preserved from the first open status
                (is (= initial-opened-date new-opened-date)
                    "Opened date should NOT be updated when transitioning between open statuses"))))))

       (testing "POST /ctia/incident/:id/status: Open → Open: Contained preserves opened but sets contained"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               ;; First transition from New to Open
               first-open-response (post-status app (uri/uri-encode test-id) "Open")
               first-open-incident (:parsed-body first-open-response)
               initial-opened-date (get-in first-open-incident [:incident_time :opened])]

           (is (= 200 (:status first-open-response)))
           (is (= "Open" (:status first-open-incident)))
           (is (some? initial-opened-date) "Initial opened date should be set")
           (is (nil? (get-in first-open-incident [:incident_time :contained])) "Contained should NOT be set yet")

           ;; Transition to Open: Contained with a later time
           (helpers/fixture-with-fixed-time
            (t/plus fixed-now (t/minutes 5))
            (fn []
              (let [response (post-status app (uri/uri-encode test-id) "Open: Contained")
                    updated-incident (:parsed-body response)
                    new-opened-date (get-in updated-incident [:incident_time :opened])
                    contained-date (get-in updated-incident [:incident_time :contained])]

                (is (= 200 (:status response)))
                (is (= "Open: Contained" (:status updated-incident)))
                ;; CRITICAL: opened should be preserved from first Open
                (is (= initial-opened-date new-opened-date)
                    "Opened date should be preserved from first Open status")
                ;; CRITICAL: contained should be set to the NEW time
                (is (some? contained-date) "Contained date should be set")
                (is (not= initial-opened-date contained-date)
                    "Contained date should be DIFFERENT from opened date")
                (is (= (tc/to-date (t/plus fixed-now (t/minutes 5))) contained-date)
                    "Contained date should be the time of transition to Open: Contained"))))))

       (testing "PATCH /ctia/incident/:id: Open → Open: Contained preserves opened but sets contained"
         (let [test-id (create-test-incident app)
               _ (swap! test-incidents conj test-id)
               ;; First transition from New to Open
               first-open-response (PATCH app
                                         (str "ctia/incident/" (uri/uri-encode test-id))
                                         :body {:status "Open"}
                                         :headers {"Authorization" "45c1f5e3f05d0"})
               first-open-incident (:parsed-body first-open-response)
               initial-opened-date (get-in first-open-incident [:incident_time :opened])]

           (is (= 200 (:status first-open-response)))
           (is (= "Open" (:status first-open-incident)))
           (is (some? initial-opened-date) "Initial opened date should be set")
           (is (nil? (get-in first-open-incident [:incident_time :contained])) "Contained should NOT be set yet")

           ;; Transition to Open: Contained with a later time
           (helpers/fixture-with-fixed-time
            (t/plus fixed-now (t/minutes 5))
            (fn []
              (let [response (PATCH app
                                   (str "ctia/incident/" (uri/uri-encode test-id))
                                   :body {:status "Open: Contained"}
                                   :headers {"Authorization" "45c1f5e3f05d0"})
                    updated-incident (:parsed-body response)
                    new-opened-date (get-in updated-incident [:incident_time :opened])
                    contained-date (get-in updated-incident [:incident_time :contained])]

                (is (= 200 (:status response)))
                (is (= "Open: Contained" (:status updated-incident)))
                ;; CRITICAL: opened should be preserved from first Open
                (is (= initial-opened-date new-opened-date)
                    "Opened date should be preserved from first Open status")
                ;; CRITICAL: contained should be set to the NEW time
                (is (some? contained-date) "Contained date should be set")
                (is (not= initial-opened-date contained-date)
                    "Contained date should be DIFFERENT from opened date")
                (is (= (tc/to-date (t/plus fixed-now (t/minutes 5))) contained-date)
                    "Contained date should be the time of transition to Open: Contained"))))))
      (finally
        ;; Clean up test incidents
        (doseq [test-id @test-incidents]
          (delete-incident app test-id))))))

(deftest test-incident-crud-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
     (let [parameters (into sut/incident-entity
                            {:app app
                             :patch-tests? true
                             :search-tests? true
                             :delete-search-tests? true
                             :example (assoc new-incident-maximal
                                             :meta
                                             {:ai-generated-description true})
                             :headers {:Authorization "45c1f5e3f05d0"}
                             :additional-tests additional-tests})]
       (entity-crud-test parameters)))))

(def ctim-severity-order
  {"Unknown" 0
   "None" 0
   "Info" 0
   "Low" 1
   "Medium" 2
   "High" 3
   "Critical" 4})

(defn gen-new-incident
  ([] (gen-new-incident "High"))
  ([severity]
   (let [order (ctim-severity-order severity)
         _ (if (some? severity)
             (assert (number? order)
                     (str "Unmapped severity " (pr-str severity)))
             (assert ((some-fn nil? number?) order)))]
     (-> new-incident-minimal
         (dissoc :id :severity)
         ;; test missing severity if nil
         (cond-> (some? order) (assoc :severity severity))
         (assoc :title (str (java.util.UUID/randomUUID))
                :revision (or order 0))))))

(s/defn create-incidents [app incidents :- (s/pred set?)]
  (bundle/import-bundle
    (-> new-bundle-minimal
        (dissoc :id)
        (assoc :incidents incidents))
    nil    ;; external-key-prefixes
    (auth/map->Identity {:login "foouser"
                         :groups ["foogroup"]})
    (app/service-graph app)))

(defn purge-incidents! [app]
  (search-th/delete-search app :incident {:query "*"
                                          :REALLY_DELETE_ALL_THESE_ENTITIES true})
  ;;FIXME ideally we pass wait_for=true to the delete search, but it yields a coercion error in ES.
  ;; instead, we query GET /incident/search/count until it is zero as a workaround
  (loop [tries 0]
    (assert (< tries 10))
    (let [;; first time around, query immediately to hopefully wake up the index to schedule a refresh
          ;; https://www.elastic.co/guide/en/elasticsearch/reference/7.17/near-real-time.html
          {count-status :status
           count-body :parsed-body} (search-th/count-raw app :incident {:query "*"})]
      (assert (= 200 count-status) (pr-str count-status))
      (when-not (zero? count-body)
        ;; refresh time is 1s. if it takes longer, we're probably on CI with limited CPU,
        ;; so wait longer to give the ES refresh as many resources as we can.
        (Thread/sleep (* 1000 (inc tries)))
        (recur (inc tries))))))

(def asset-000-ttp-000 {:asset 0   :ttp 0})
(def asset-000-ttp-100 {:asset 0   :ttp 100})
(def asset-002-ttp-004 {:asset 2   :ttp 4})
(def asset-002-ttp-006 {:asset 2   :ttp 6})
(def asset-004-ttp-002 {:asset 4   :ttp 2})
(def asset-006-ttp-002 {:asset 6   :ttp 2})
(def asset-100-ttp-000 {:asset 100 :ttp 0})
(def asset-100-ttp-100 {:asset 100 :ttp 100})

(def shrink-sort-scores-test?
  "If true, enable shrinking in sort-scores-test."
  false)

(deftest sort-scores-test
  (es-helpers/for-each-es-version
    "Can sort by multiple scores"
    [7]
    #(es-helpers/clean-es-state! % "ctia_*")
    (helpers/with-properties (-> ["ctia.auth.type" "allow-all"]
                                 (into es-helpers/basic-auth-properties)
                                 (conj "ctia.http.incident.score-types" "asset,ttp"
                                       "ctia.store.bulk-refresh" "wait_for"))
      (helpers/fixture-ctia-with-app
        (fn [app]
          ;(helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
          ;(whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
          (let [;; ordered from least to most complex
                all-scoring-test-cases (-> []
                                           ;; one score per incident
                                           (into (mapcat (fn [asc?]
                                                           (let [incident-count 10
                                                                 ->sort_by (s/fn [score-type :- (s/pred simple-keyword?)]
                                                                             (format "scores.%s:%s" (name score-type) (if asc? "asc" "desc")))
                                                                 ->expected-score-order (s/fn [score-type :- (s/pred simple-keyword?)]
                                                                                          ((if asc? identity rseq)
                                                                                           (mapv (fn [score]
                                                                                                   {score-type score})
                                                                                                 (range incident-count))))]
                                                             [;; simple asset sort
                                                              {:test-id (if asc? :asc-asset-single :desc-asset-single)
                                                               :sort_by (->sort_by :asset)
                                                               :expected-score-order (->expected-score-order :asset)}
                                                              ;; simple ttp sort
                                                              {:test-id (if asc? :asc-ttp-single :desc-ttp-single)
                                                               :sort_by (->sort_by :ttp)
                                                               :expected-score-order (->expected-score-order :ttp)}])))
                                                 [true false])
                                           ;; multiple scores per incident
                                           (into (mapcat (fn [asc?]
                                                           (let [reorder (if asc? identity rseq)]
                                                             [;; simple asset sort
                                                              {:test-id (if asc? :asc-asset-multi :desc-asset-multi)
                                                               :sort_by (str "scores.asset:" (if asc? "asc" "desc"))
                                                               :expected-score-order (reorder [asset-000-ttp-000
                                                                                               asset-002-ttp-004
                                                                                               asset-004-ttp-002
                                                                                               asset-100-ttp-100])}
                                                              ;; simple ttp sort
                                                              {:test-id (if asc? :asc-ttp-multi :desc-ttp-multi)
                                                               :sort_by (str "scores.ttp:" (if asc? "asc" "desc"))
                                                               :expected-score-order (reorder [asset-000-ttp-000
                                                                                               asset-004-ttp-002
                                                                                               asset-002-ttp-004
                                                                                               asset-100-ttp-100])}])))
                                                 [true false])
                                           ;; composite sort_by param
                                           (into [{:test-id :asset-desc-then-ttp-asc
                                                   :sort_by "scores.asset:desc,scores.ttp:asc"
                                                   :expected-score-order [asset-100-ttp-000
                                                                          asset-100-ttp-100
                                                                          asset-002-ttp-004
                                                                          asset-002-ttp-006
                                                                          asset-000-ttp-000
                                                                          asset-000-ttp-100]}
                                                  {:test-id :asset-desc-then-ttp-desc
                                                   :sort_by "scores.asset:desc,scores.ttp:desc"
                                                   :expected-score-order [asset-100-ttp-100
                                                                          asset-100-ttp-000
                                                                          asset-002-ttp-006
                                                                          asset-002-ttp-004
                                                                          asset-000-ttp-100
                                                                          asset-000-ttp-000]}
                                                  {:test-id :ttp-desc-then-asset-asc
                                                   :sort_by "scores.ttp:desc,scores.asset:asc"
                                                   :expected-score-order [asset-000-ttp-100
                                                                          asset-100-ttp-100
                                                                          asset-004-ttp-002
                                                                          asset-006-ttp-002
                                                                          asset-000-ttp-000
                                                                          asset-100-ttp-000]}
                                                  {:test-id :ttp-desc-then-asset-desc
                                                   :sort_by "scores.ttp:desc,scores.asset:desc"
                                                   :expected-score-order [asset-100-ttp-100
                                                                          asset-000-ttp-100
                                                                          asset-006-ttp-002
                                                                          asset-004-ttp-002
                                                                          asset-100-ttp-000
                                                                          asset-000-ttp-000]}]))
                _ (assert (apply distinct? (map :test-id all-scoring-test-cases)))
                _ (assert (every? #(apply distinct? (:expected-score-order %)) all-scoring-test-cases))
                ;; only show failures for one case at a time, simplest first
                tests-failed? (volatile! false)]
            (doseq [{:keys [test-id sort_by expected-score-order]} all-scoring-test-cases
                    :when (not @tests-failed?)]
              (checking (pr-str test-id)
                {:num-tests (if shrink-sort-scores-test? 10 1)}
                [expected-score-order ((if shrink-sort-scores-test? gen'/subsequence gen/return)
                                       expected-score-order)
                 :when (< 1 (count expected-score-order))]
                (try (or (let [incidents-count (count expected-score-order)
                               incidents (into #{} (shuffle (map #(assoc (gen-new-incident) :scores %)
                                                                 expected-score-order)))
                               _ (create-incidents app incidents)
                               {:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:sort_by sort_by})]
                           (when (is (= 200 (:status raw)) (pr-str raw))
                             (let [scores->order (into {} (map-indexed (fn [i score] {score i}))
                                                       expected-score-order)
                                   expected-parsed-body (sort-by (fn [incident]
                                                                   {:post [(number? %)]}
                                                                   (scores->order (:scores incident)))
                                                                 parsed-body)
                                   _ (assert (= expected-score-order (map :scores expected-parsed-body)))]
                               (and (is (= incidents-count (count parsed-body)) (pr-str raw))
                                    (is (= incidents-count (count expected-parsed-body)) (pr-str raw))
                                    (is (= expected-score-order
                                           (mapv :scores parsed-body)))
                                    (is (= expected-parsed-body
                                           parsed-body))))))
                         (vreset! tests-failed? true))
                   (finally (purge-incidents! app)))))))))))

;; extracted from the much more thorough severity-int-script-search
(deftest simple-severity-int-script-search-test
  (es-helpers/for-each-es-version
    "severity sorts like #'ctim-severity-order"
    [7]
    #(es-helpers/clean-es-state! % "ctia_*")
    (helpers/with-properties (into ["ctia.auth.type" "allow-all"]
                                   es-helpers/basic-auth-properties)
      (helpers/fixture-ctia-with-app
        (fn [app]
          ;(helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
          ;(whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
          (let [fixed-severities-asc (vec (concat ["Info" "Low" "Medium" "High"]
                                                  (repeat 10 "Critical")))]
            (try (testing (pr-str fixed-severities-asc)
                   (let [incidents-count (count fixed-severities-asc)
                         incidents (into #{}
                                         (map gen-new-incident)
                                         fixed-severities-asc)]
                     (create-incidents app incidents)
                     (doseq [asc? [true false]
                             :let [test-id {:asc? asc?}]]
                       (testing (pr-str test-id)
                         (let [{:keys [parsed-body] :as raw}
                               (search-th/search-raw app :incident {:sort_by
                                                                    (format "severity:%1$s,timestamp:%1$s"
                                                                            (if asc? "asc" "desc"))})
                               expected-parsed-body (sort-by (fn [incident]
                                                               {:post [(number? %)]}
                                                               (ctim-severity-order (:severity incident)))
                                                             (if asc?
                                                               #(compare %1 %2)
                                                               #(compare %2 %1))
                                                             parsed-body)
                               critical-timestamps (map (comp jt/to-millis-from-epoch :timestamp)
                                                        (filter #(= "Critical" (:severity %))
                                                                parsed-body))]
                           (assert (seq critical-timestamps))
                           (is (apply (if asc? <= >=) critical-timestamps))
                           (and (is (= 200 (:status raw)) (pr-str raw))
                                (is (= incidents-count (count parsed-body)) (pr-str raw))
                                (is (= incidents-count (count expected-parsed-body)) (pr-str raw))
                                ;; use fixed-severities-asc directly to mitigate mistakes
                                ;; in calculating expected-parsed-body (eg., faulty comparator)
                                (is (= ((if asc? identity rseq) fixed-severities-asc)
                                       (map :severity parsed-body)))
                                (is (= expected-parsed-body
                                       parsed-body))))))))
            (finally (purge-incidents! app)))))))))

(deftest sort-incidents-by-tactics-test
  (es-helpers/for-each-es-version
    "sort by tactics"
    [7]
    #(es-helpers/clean-es-state! % "ctia_*")
    (helpers/with-properties (into ["ctia.auth.type" "allow-all"]
                                   es-helpers/basic-auth-properties)
      (helpers/fixture-ctia-with-app
        (fn [app]
          ;(helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
          ;(whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
          (try (let [ascending-tactics [["bad-id"] ;; 0
                                        ["TA0042"] ;; 1
                                        ["TA0043"] ;; 2
                                        ["TA0043" "TA0001"] ;; 3
                                        ["bad-id" "TA0003"] ;; 9
                                        ["TA0002" "TA0043"] ;; 11
                                        ]
                     ascending-incidents (mapv #(assoc (gen-new-incident) :tactics %) ascending-tactics)]
                 (create-incidents app (-> ascending-incidents shuffle set))
                 (testing "tactics"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:sort_by "tactics"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= ascending-tactics
                                 (map :tactics parsed-body))))))
                 (testing "tactics:desc"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:sort_by "tactics:desc"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= (rseq ascending-tactics)
                                 (map :tactics parsed-body)))))))
               (finally (purge-incidents! app)))
          (try (let [ascending-incidents [;; first 3 have equivalent tactics scores (9)
                                          (assoc (gen-new-incident) :tactics ["TA0003"] :title "B")
                                          (assoc (gen-new-incident) :tactics ["TA0003" "TA0001"] :title "C")
                                          (assoc (gen-new-incident) :tactics ["TA0042" "TA0007"] :title "D")
                                          ;; higher tactic score (10)
                                          (assoc (gen-new-incident) :tactics ["TA0006"] :title "A")]]
                 (create-incidents app (-> ascending-incidents shuffle set))
                 (testing "tactics,title"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:sort_by "tactics,title"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= ["B" "C" "D" "A"]
                                 (map :title parsed-body))))))
                 (testing "tactics,title:desc"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:sort_by "tactics,title:desc"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= ["D" "C" "B" "A"]
                                 (map :title parsed-body))))))
                 (testing "tactics:desc,title"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:sort_by "tactics:desc,title"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= ["A" "B" "C" "D"]
                                 (map :title parsed-body)))))))
               (finally (purge-incidents! app))))))))

(defmacro result+ms-time
  "Evaluates expr and returns a tuple [result ms-time] where result is the 
   result of the expr and ns-time is the milliseconds duration of expr."
  [expr]
  `(let [start# (System/nanoTime)
         ret# ~expr
         end# (System/nanoTime)
         ms-time# (/ (double (- end# start#)) 1000000.0)]
     [ret# ms-time#]))

(defn severity-int-script-search
  "If :bench-atom is provided, tests huge cases. Otherwise,
  performs small unit tests."
  ([] (severity-int-script-search {}))
  ([{:keys [bench-atom]}]
   (es-helpers/for-each-es-version
     "severity sorts like #'ctim-severity-order"
     [7]
     #(es-helpers/clean-es-state! % "ctia_*")
     (helpers/with-properties (into ["ctia.auth.type" "allow-all"]
                                    es-helpers/basic-auth-properties)
       (helpers/fixture-ctia-with-app
         (fn [app]
           ;(helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
           ;(whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
           (doseq [;; only one ordering with these severities. don't mix any of Info, Unknown, None, or nil in the same test.
                   canonical-fixed-severities-asc (-> []
                                                      (cond-> (not bench-atom)
                                                        (into [["Unknown" "Low"]
                                                               ["Unknown" "Critical"]
                                                               ["None" "Low"]
                                                               ["None" "Critical"]
                                                               [nil "Low"]
                                                               [nil "Critical"]
                                                               ["Info" "Low"]
                                                               ["Info" "Critical"]
                                                               ["Low" "Medium" "High" "Critical"]
                                                               ;; missing severity is the same as None/Unknown
                                                               [nil "Low" "Medium" "High" "Critical"]
                                                               ["Unknown" "Low" "Medium" "High" "Critical"]]))
                                                      ;; only benchmark the largest test case because the benchmark is dominated
                                                      ;; by the bundle import
                                                      (into [["None" "Low" "Medium" "High" "Critical"]]))
                   ;; scale up the test size by repeating elements
                   multiplier (if-not bench-atom
                                [1 2]
                                [#_1 #_10 #_100 #_1000 #_5000 20000])
                   ;; expand the incidents test data
                   :let [fixed-severities-asc (into [] (mapcat #(repeat multiplier %))
                                                    canonical-fixed-severities-asc)]]
             (try (testing (pr-str fixed-severities-asc)
                    (let [incidents-count (count fixed-severities-asc)
                          ;; note: there's a default limit of 10k results via index.max_result_window
                          result-size (cond-> incidents-count
                                        ;; spend less time parsing results during benchmarks
                                        bench-atom
                                        (min 10))
                          incidents (into (sorted-set-by #(compare (:title %1) (:title %2))) ;; a (possibly vain) attempt to randomize the order in which ES will index
                                          (map gen-new-incident)
                                          fixed-severities-asc)
                          _ (assert (= (count fixed-severities-asc) (count incidents))
                                    (format "Bad sorted-set-by call\ncase: %s, multiplier %s, expected incidents: %s, actual:"
                                            canonical-fixed-severities-asc
                                            multiplier
                                            (count fixed-severities-asc)
                                            (count incidents)))
                          [_created-bundle create-incidents-ms-time] (result+ms-time (create-incidents app incidents))
                          _ (when bench-atom
                              (println (format "Took %ems to import %s incidents" create-incidents-ms-time (str incidents-count))))
                          _ (doseq [sort_by (cond-> ["severity"]
                                              bench-atom (conj
                                                           ;; hijacking this int field for perf comparison, see `gen-new-incident`
                                                           "revision"
                                                           ;; no sorting baseline
                                                           nil))
                                    asc? [true false]
                                    iteration (range (if bench-atom 5 1))
                                    :let [search-params (cond-> {:limit result-size}
                                                          sort_by (assoc :sort_by sort_by
                                                                         :sort_order (if asc? "asc" "desc")))
                                          test-id {:iteration iteration :sort_by sort_by :asc? asc? :search-params search-params
                                                   :incidents-count incidents-count :result-size result-size}]]
                              (testing (pr-str test-id)
                                (let [_ (when bench-atom
                                          (println)
                                          (println "Benchmarking..." (pr-str test-id)))
                                      [{:keys [parsed-body] :as raw} ms-time]
                                      (result+ms-time
                                       (search-th/search-raw app :incident search-params))

                                      expected-parsed-body (sort-by (fn [{:keys [severity] :as incident}]
                                                                      {:post [(number? %)]}
                                                                      (let [c (ctim-severity-order severity)]
                                                                        (when severity
                                                                          (assert (number? c)
                                                                                  (str "No severity ordering for " (pr-str severity)
                                                                                       "\n" (pr-str incident))))
                                                                        (or c 0)))
                                                                    (if asc?
                                                                      #(compare %1 %2)
                                                                      #(compare %2 %1))
                                                                    parsed-body)

                                      success? (and (is (= 200 (:status raw)) (when (= 1 multiplier) (pr-str raw)))
                                                    (is (= result-size (count parsed-body)) (when (= 1 multiplier) (pr-str raw)))
                                                    (is (= result-size (count expected-parsed-body)) (when (= 1 multiplier) (pr-str raw)))
                                                    (or (not sort_by) ;; don't check non-sorting baseline benchmark
                                                        (and ;; use fixed-severities-asc directly to mitigate mistakes
                                                             ;; in calculating expected-parsed-body (eg., faulty comparator)
                                                             (is (= (->> ((if asc? identity rseq) fixed-severities-asc)
                                                                         ;; entire query is checked in unit tests, bench uses a subset
                                                                         (take result-size))
                                                                    (map :severity parsed-body)))
                                                             ;; should succeed even with multipliers because sort-by is stable
                                                             (is (= expected-parsed-body
                                                                    parsed-body)))))]
                                  (when bench-atom
                                    (assert success?)
                                    (-> (swap! bench-atom update-in [canonical-fixed-severities-asc incidents-count sort_by]
                                               (fn [prev]
                                                 (let [nxt (-> prev
                                                               (update :ms-times (fnil conj []) ms-time)
                                                               ((fn [{:keys [ms-times] :as res}]
                                                                  (assoc res :ms-avg (format "%e" (double (/ (apply + ms-times) (count ms-times))))))))
                                                       _ (do ;; dirty side effects in swap!. note: atom access is seralized for now
                                                             (println)
                                                             (println (format "Benchmark %s" (pr-str sort_by)))
                                                             (println (format "Case: %s %s (%sth iteration)"
                                                                              (pr-str canonical-fixed-severities-asc)
                                                                              (if asc? "ascending" "descending")
                                                                              (str iteration)))
                                                             (println (format "Multiplier: %s (incident count: %s)" (str multiplier) (str incidents-count)))
                                                             (println (format "Duration: %ems" ms-time))
                                                             (println (format "Average: %sms" (:ms-avg nxt))))]
                                                   nxt))))))))]))
                  (finally (purge-incidents! app))))))))))

(deftest test-incident-severity-int-search
  (severity-int-script-search))

(deftest ^:disabled bench-incident-severity-int-search
  (let [results (atom {})
        id (str (java.util.UUID/randomUUID))
        file (format "bench-result-%s.edn" id)]
    (severity-int-script-search
      {:bench-atom results})
    (prn "Saved to file" file)
    ((requiring-resolve 'clojure.pprint/pprint) @results)
    (spit file @results)))

(deftest test-incident-metric-routes
  (test-metric-routes (into sut/incident-entity
                            {:entity-minimal new-incident-minimal
                             :enumerable-fields sut/incident-enumerable-fields
                             :date-fields sut/incident-histogram-fields
                             :average-fields sut/incident-average-fields})))

(deftest test-incident-routes-access-control
  (access-control-test "incident"
                       new-incident-minimal
                       true
                       true
                       test-for-each-store-with-app))

(deftest filter-incidents-by-tactics-test
  (es-helpers/for-each-es-version
    "sort by tactics"
    [7]
    #(es-helpers/clean-es-state! % "ctia_*")
    (helpers/with-properties (into ["ctia.auth.type" "allow-all"]
                                   es-helpers/basic-auth-properties)
      (helpers/fixture-ctia-with-app
        (fn [app]
          ;(helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
          ;(whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
          (try (let [incident1 (assoc (gen-new-incident) :tactics ["TA0002" "TA0043" "TA0006"])
                     incident2 (assoc (gen-new-incident) :tactics ["TA0004" "TA0043" "TA0008"])
                     incident3 (assoc (gen-new-incident) :tactics ["TA0008" "TA0043" "TA0006" "TA8888"])
                     normalize (fn [incidents]
                                 (->> incidents
                                      (map #(select-keys % [:title :tactics]))
                                      (sort-by :tactics)))]
                 (create-incidents app #{incident1 incident2 incident3})
                 (testing "incident1"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:query "tactics:(\"TA0002\")"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= (normalize [incident1])
                                 (normalize parsed-body))
                              (pr-str parsed-body)))))
                 (testing "incident1+2+3"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:query "tactics:(\"TA0043\")"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= (normalize [incident1 incident2 incident3])
                                 (normalize parsed-body))))))
                 (testing "incident1+3 multi"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:query "tactics:(\"TA0002\" || \"TA8888\")"}) ]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= (normalize [incident1 incident3])
                                 (normalize parsed-body)))))))
               (finally (purge-incidents! app))))))))

(deftest filter-incidents-by-sources-test
  (es-helpers/for-each-es-version
    "sort by tactics"
    [7]
    #(es-helpers/clean-es-state! % "ctia_*")
    (helpers/with-properties (into ["ctia.auth.type" "allow-all"]
                                   es-helpers/basic-auth-properties)
      (helpers/fixture-ctia-with-app
        (fn [app]
          ;(helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
          ;(whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
          (try (let [incident1 (assoc (gen-new-incident) :detection_sources ["Crowdstrike for Endpoint" "Cisco XDR Detections" "TA0006"])
                     incident2 (assoc (gen-new-incident) :detection_sources ["TA0004" "Cisco XDR Detections" "TA0008"])
                     incident3 (assoc (gen-new-incident) :detection_sources ["TA0008" "Cisco XDR Detections" "TA0006" "Talnos, which is like Talos but weird"])
                     normalize (fn [incidents]
                                 (->> incidents
                                      (map #(select-keys % [:title :detection_sources]))
                                      (sort-by :detection_sources)))]
                 (create-incidents app #{incident1 incident2 incident3})
                 (testing "incident1"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:query "detection_sources:(\"Crowdstrike for Endpoint\")"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= (normalize [incident1])
                                 (normalize parsed-body))
                              (pr-str parsed-body)))))
                 (testing "incident1+2+3"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:query "detection_sources:(\"Cisco XDR Detections\")"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= (normalize [incident1 incident2 incident3])
                                 (normalize parsed-body))))))
                 (testing "incident1+3 multi"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:query "detection_sources:(\"Crowdstrike for Endpoint\" || \"Talnos, which is like Talos but weird\")"}) ]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= (normalize [incident1 incident3])
                                 (normalize parsed-body)))))))
               (finally (purge-incidents! app))))))))

(deftest filter-incidents-by-scores-range
  (es-helpers/for-each-es-version
    "filter by scores"
    [7]
    #(es-helpers/clean-es-state! % "ctia_*")
    (helpers/with-properties (-> ["ctia.auth.type" "allow-all"]
                                 (into es-helpers/basic-auth-properties)
                                 (conj "ctia.http.incident.score-types" "asset,ttp"))
      (helpers/fixture-ctia-with-app
        (fn [app]
          ;(helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
          ;(whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
          (try (let [incident1 (assoc (gen-new-incident)
                                      :title "incident1"
                                      :assignees ["assignee1"]
                                      :scores {:ttp 30
                                               :asset 100})
                     incident2 (assoc (gen-new-incident)
                                      :title "incident2"
                                      :scores {:ttp 50
                                               :asset 50})
                     incident3 (assoc (gen-new-incident)
                                      :title "incident3"
                                      :scores {:ttp 70
                                               :asset 0})
                     normalize (fn [incidents]
                                 (->> incidents
                                      (map #(select-keys % [:title :scores]))
                                      (sort-by :title)))]
                 (create-incidents app #{incident1 incident2 incident3})
                 (testing "50<=ttp"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:query "scores.ttp:>=50"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= (normalize [incident2 incident3])
                                 (normalize parsed-body))
                              (pr-str parsed-body)))))
                 (testing "ttp<=50"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:query "scores.ttp:<=50"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= (normalize [incident1 incident2])
                                 (normalize parsed-body))))))
                 (testing "50<=ttp && asset<=60"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:query "(scores.ttp:<=50) AND (scores.asset:>=60)"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= (normalize [incident1])
                                 (normalize parsed-body))))))
                 (testing "combine with filter-map"
                   (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:assignees ["assignee1"]
                                                                                            :query "scores.ttp:<=50"})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= (normalize [incident1])
                                 (normalize parsed-body)))))))
               (finally (purge-incidents! app))))))))

(def incident-statuses
  (:vs (st/get-in sut/Incident [:status])))

(deftest compute-intervals-test
  (let [new-statuses ["New" "New: Processing" "New: Presented"]
        open-statuses ["Open" "Open: Recovered" "Open: Contained" "Open: Reported" "Open: Investigating"]
        hold-statuses ["Hold" "Hold: External" "Hold: Internal" "Hold: Legal"]
        closed-statuses ["Closed" "Closed: Confirmed Threat" "Closed: False Positive" "Closed: Merged"
                         "Closed: Near-Miss" "Closed: Other" "Closed: Suspected" "Closed: Under Review"]
        ;; If new statuses are added that begin with New, Closed, Open, or Hold,
        ;; we might want to re-evaluate this function.
        _ (assert (every? (set (concat new-statuses open-statuses closed-statuses hold-statuses))
                          (filter #(some (fn [prefix]
                                           (string/starts-with? % prefix))
                                         ["Open" "Closed" "New" "Hold"])
                                  incident-statuses)))
        computed-interval 20
        earlier (jt/java-date)
        later   (-> (jt/instant earlier) (jt/plus (jt/seconds computed-interval)) jt/java-date)
        prev (-> incident-minimal
                 (assoc :groups []
                        :created earlier
                        :owner ""
                        :id "foo")
                 (dissoc :intervals))]
    (testing "updating new_to_opened"
      (let [prev (assoc prev :status "New: Presented")
            incident (assoc prev :status "Open" :incident_time {:opened later})]
        (testing "if prev does not already have a :new_to_opened interval, compute interval and include in update"
          (is (= (assoc incident :intervals {:new_to_opened computed-interval})
                 (sut/compute-intervals prev incident))))
        (testing "prefer existing :new_to_opened interval"
          (is (= (assoc incident :intervals {:new_to_opened 55565})
                 (sut/compute-intervals (assoc prev :intervals {:new_to_opened 55565})
                                        incident))))
        (doseq [open-status open-statuses]
          (testing (format "Status, '%s', is treated as open" open-status)
            (let [incident (assoc prev :status open-status :incident_time {:opened later})]
              (is (= (assoc incident :intervals {:new_to_opened computed-interval})
                     (sut/compute-intervals prev incident))))))
        (testing "if previous status is one of the 'New' sub-statuses, then create interval"
          (doseq [stored-status (shuffle new-statuses)]
            (testing stored-status
              (is (= (assoc incident :intervals {:new_to_opened computed-interval})
                     (sut/compute-intervals (assoc prev :status stored-status) incident))))))
        (testing "if previous status is not one of the 'New' sub-statuses, then don't create new interval"
          (doseq [stored-status (shuffle (apply disj incident-statuses new-statuses))]
            (testing stored-status
              (is (= incident (sut/compute-intervals (assoc prev :status stored-status) incident))))))
        (testing "if new status is not one of the 'Open' sub-statuses, then don't create new interval"
          (doseq [new-status (shuffle (apply disj incident-statuses open-statuses))]
            (testing new-status
              (let [updated-incident (assoc incident :status new-status)]
                (is (= updated-incident (sut/compute-intervals prev updated-incident)))))))
        ;; Note: :incident_time.opened is included in stored incident due to the Incident schema.
        ;; This test is sufficient to show that this is ignored by sut/compute-intervals,
        ;; and only the :incident_time.opened in the updated incident is used to calculate the interval.
        (testing "if :created is after the updated :incident_time.opened, elide interval from update"
          (let [prev (assoc prev :created later)
                incident (assoc prev :status "Open" :incident_time {:opened earlier})]
            (is (= incident
                   (sut/compute-intervals prev incident)))))))
    (testing "updating opened_to_closed"
      (let [prev (assoc prev :status "Open: Recovered" :incident_time {:opened earlier})
            incident (-> prev
                         (assoc :status "Closed: Other")
                         (assoc-in [:incident_time :closed] later))]
        (doseq [closed-status closed-statuses]
          (testing (format "Status, '%s', is treated as closed" closed-status)
            (let [incident (assoc incident :status closed-status)]
              (is (= (assoc incident :intervals {:opened_to_closed computed-interval})
                     (sut/compute-intervals prev incident))))))
        (doseq [open-status open-statuses]
          (testing (format "Status, '%s', is treated as open" open-status)
            (let [prev (assoc prev :status open-status)]
              (is (= (assoc incident :intervals {:opened_to_closed computed-interval})
                     (sut/compute-intervals prev incident))))))
        (doseq [hold-status hold-statuses]
          (testing (format "Status, '%s', is treated as hold and should trigger opened_to_closed" hold-status)
            (let [prev (assoc prev :status hold-status)]
              (is (= (assoc incident :intervals {:opened_to_closed computed-interval})
                     (sut/compute-intervals prev incident))))))
        (testing "if previous status is not Open or Hold, then don't create new interval"
          (doseq [stored-status (shuffle (apply disj incident-statuses (concat open-statuses hold-statuses)))]
            (testing stored-status
              (is (= incident (sut/compute-intervals (assoc prev :status stored-status) incident))))))
        (testing "if :stored-incident does not already have an :opened_to_closed interval, compute it"
          (is (= (assoc-in incident [:intervals :opened_to_closed] computed-interval)
                 (sut/compute-intervals prev incident))))
        (testing "if :stored-incident already has an :opened_to_closed interval, don't add it to the update"
          (let [prev (assoc prev :intervals {:opened_to_closed (* 2 (rand-int (inc computed-interval)))})]
            (is (= (assoc incident :intervals (:intervals prev))
                   (sut/compute-intervals prev incident)))))
        (testing "if :incident_time.opened is after the updated :incident_time.closed, elide interval from update"
          (let [prev (assoc prev :incident_time {:opened later})
                incident (-> incident (assoc :incident_time {:opened later :closed earlier}))]
            (is (= incident (sut/compute-intervals prev incident)))))))
    (testing "updating new_to_contained"
      (let [prev (assoc prev :status "New: Presented")
            incident (-> (assoc prev :status "Open: Contained")
                         (assoc-in [:incident_time :contained] later))]
        (testing "prefer existing :new_to_contained interval"
          (is (= (assoc incident :intervals {:new_to_contained 55565})
                 (sut/compute-intervals (assoc prev :intervals {:new_to_contained 55565})
                                        incident))))
        (testing "if previous is a 'New' or 'Open' status, then create new interval"
          (doseq [stored-status (shuffle (concat new-statuses open-statuses))]
            (testing stored-status 
              (is (= (assoc incident :intervals {:new_to_contained computed-interval})
                     (sut/compute-intervals (assoc prev :status stored-status) incident))))))
        (testing "if previous status is not one of the 'New' or 'Open' sub-statuses, then don't create new interval"
          (doseq [stored-status (shuffle (apply disj incident-statuses (concat new-statuses open-statuses)))]
            (testing stored-status
              (is (= incident (sut/compute-intervals (assoc prev :status stored-status) incident))))))
        (testing "if new status is not Open: Contained, then don't create new interval"
          (doseq [new-status (shuffle (disj incident-statuses "Open: Contained"))]
            (testing new-status
              (let [updated-incident (assoc incident :status new-status)]
                (is (= updated-incident (sut/compute-intervals prev (assoc updated-incident :status new-status))))))))
        (testing "if :created is after the updated :incident_time.contained, elide interval from update"
          (let [prev (assoc prev :created later)
                incident (-> (assoc prev :status "Open: Contained")
                             (assoc-in [:incident_time :contained] earlier))]
            (is (= incident
                   (sut/compute-intervals prev incident)))))))))

(s/defn create-incident-at-time :- s/Str
  [app
   time :- s/Inst
   incident :- sut/NewIncident]
  (let [time (jt/java-date time)
        {[{incident-id :id}] :results :as results}
        (helpers/fixture-with-fixed-time
          time
          #(create-incidents app #{incident}))
        resp (get-incident app incident-id)
        created (-> resp :parsed-body :timestamp)]
    (assert (= time created)
            [time created])
    incident-id))

(deftest incident-average-metrics-test
  (test-for-each-store-with-app
    (fn [app]
      (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
      (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
      (try (let [epoch (jt/instant)
                 first-created 0 ;;offsets in seconds from epoch to created
                 second-created 10
                 third-created 20
                 ;; new_to_opened should always be recorded when
                 ;; new_to_contained is, as 'Open: Contained' is an Open status.
                 first-new_to_contained 100 ;;offsets in seconds from created to opened
                 second-new_to_opened 200
                 third-new_to_contained 500
                 first-opened_to_closed 150 ;;offsets in seconds from opened to closed
                 second-opened_to_closed 250
                 third-opened_to_closed 550
                 incident->status-changes (take 3 [[(assoc (gen-new-incident) :status "New" :title "incident1")
                                                    {:created first-created
                                                     :new_to_opened first-new_to_contained
                                                     :opened-status "Open: Contained"
                                                     :opened_to_closed first-opened_to_closed
                                                     :closed-status "Closed"}]
                                                   [(assoc (gen-new-incident) :status "New: Processing" :title "incident2")
                                                    {:created second-created
                                                     :new_to_opened second-new_to_opened
                                                     :opened-status "Open: Investigating"
                                                     :opened_to_closed second-opened_to_closed
                                                     :closed-status "Closed: False Positive"}]
                                                   [(assoc (gen-new-incident) :status "New: Presented" :title "incident3")
                                                    {:created third-created
                                                     :new_to_opened third-new_to_contained
                                                     :opened-status "Open: Contained"
                                                     :opened_to_closed third-opened_to_closed
                                                     :closed-status "Closed: Merged"}]])
                 +sec #(jt/plus epoch (jt/seconds %))
                 incident-ids (mapv (fn [[incident {:keys [created]}]]
                                      ;; create extra incidents whose statuses are never changed to simulate a real environment
                                      (second
                                        (repeatedly
                                          2
                                          #(create-incident-at-time app (jt/java-date (+sec created)) incident))))
                                    incident->status-changes)
                 incident-id->incident+intervals (map vector incident-ids incident->status-changes)
                 _ (assert (= (count incident-id->incident+intervals) (count incident->status-changes))
                           incident-ids)
                 _ (testing "populate intervals"
                     (doseq [[incident-id [incident {:keys [created new_to_opened opened-status opened_to_closed closed-status]}]] incident-id->incident+intervals
                             [new-status next-time] [[opened-status (+sec (+ created new_to_opened))]
                                                     [closed-status (+sec (+ created new_to_opened opened_to_closed))]]]
                       (testing (pr-str [new-status incident])
                         (let [response (helpers/fixture-with-fixed-time
                                          (jt/java-date next-time)
                                          #(post-status app (uri/uri-encode incident-id) new-status))]
                           (assert (= 200 (:status response))
                                   (pr-str response))))))
                 avg #(quot (apply + %&) (count %&))]
             (testing "average aggregation"
               (helpers/fixture-with-fixed-time
                 (jt/java-date (jt/plus epoch (jt/days 1))) ;;default `to` query param
                 (fn []
                   ;; the average of intervals.<field> should involve <expected-count> incidents and
                   ;; have value <expected-average> between time window <from> and <to> (latter is optional).
                   (doseq [[field expected-count expected-average from to :as test-case]
                           [["new_to_opened" 3 (avg first-new_to_contained second-new_to_opened third-new_to_contained) first-created]
                            ["new_to_opened" 2 (avg second-new_to_opened third-new_to_contained) second-created]
                            ["new_to_opened" 2 (avg first-new_to_contained second-new_to_opened) first-created (inc second-created)]
                            ["new_to_opened" 1 first-new_to_contained first-created (inc first-created)]
                            ["new_to_opened" 1 second-new_to_opened second-created (inc second-created)]
                            ["new_to_opened" 1 third-new_to_contained third-created (inc third-created)]
                            ["new_to_opened" 1 third-new_to_contained third-created]
                            ["new_to_opened" 0 nil (inc third-created)]
                            ["new_to_contained" 2 (avg first-new_to_contained third-new_to_contained) first-created]
                            ["new_to_contained" 1 third-new_to_contained second-created]
                            ["new_to_contained" 1 first-new_to_contained first-created (inc second-created)]
                            ["new_to_contained" 1 first-new_to_contained first-created (inc first-created)]
                            ["new_to_contained" 0 nil second-created (inc second-created)]
                            ["new_to_contained" 1 third-new_to_contained third-created (inc third-created)]
                            ["new_to_contained" 1 third-new_to_contained third-created]
                            ["new_to_contained" 0 nil (inc third-created)]
                            ["opened_to_closed" 3 (avg first-opened_to_closed second-opened_to_closed third-opened_to_closed) first-created]
                            ["opened_to_closed" 2 (avg second-opened_to_closed third-opened_to_closed) second-created]
                            ["opened_to_closed" 1 third-opened_to_closed third-created]
                            ["opened_to_closed" 0 nil (inc third-created)]]]
                     (testing (pr-str test-case)
                       (let [{:keys [parsed-body] :as raw} (GET app "ctia/incident/metric/average"
                                                                :headers {"Authorization" "45c1f5e3f05d0"}
                                                                :query-params (cond-> {:aggregate-on (str "intervals." field)
                                                                                       :from (+sec from)}
                                                                                to (assoc :to (+sec to))))]

                         (and (is (= 200 (:status raw)) (pr-str raw))
                              (is (= expected-count (some-> (get-in raw [:headers "X-Total-Hits"]) Integer/parseInt))
                                  raw)
                              (is (= expected-average
                                     (some-> (get-in parsed-body [:data :intervals (keyword field)]) Math/floor long))
                                  (pr-str parsed-body))))))))))
           (finally (purge-incidents! app))))))

(deftest incident-realize-timestamp-test
  (testing "Ensure that Incident timestamps remain unchanged."
    (test-for-each-store-with-app
     (fn [app]
       (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
       (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
       (let [incidents #{(gen-new-incident)}
             ext-key-prefixes nil
             auth-ident (auth/map->Identity {:login "foouser"
                                             :groups ["foogroup"]})
             imported (bundle/import-bundle
                       (-> new-bundle-minimal
                           (dissoc :id)
                           (assoc :incidents incidents))
                       ext-key-prefixes
                       auth-ident
                       (app/service-graph app))]
         (let [incident-id (->> imported :results first :id id/long-id->id :short-id)
               statuses ["New" "Open" "Stalled" "Incident Reported"]
               timestamps (->>
                           statuses
                           (map #(post-status app incident-id %))
                           (map (comp :timestamp :parsed-body))
                           doall)]
           (is (apply = timestamps))))))))
