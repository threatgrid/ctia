(ns ctia.entity.incident-test
  (:require [clj-momo.lib.clj-time
             [coerce :as tc]
             [core :as t]]
            ductile.index
            [ctia.test-helpers.search :as search-th]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.auth.threatgrid :as auth]
            [ctia.bundle.core :as bundle]
            [ctim.schemas.vocabularies :as vocab]
            [ctim.examples.bundles :refer [new-bundle-minimal]]
            [ctim.examples.incidents :refer [new-incident-minimal]]
            [ctia.entity.incident :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [PATCH POST]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctia.test-helpers.es :as es-helpers]
            [puppetlabs.trapperkeeper.app :as app]
            [ctim.examples.incidents
             :refer
             [new-incident-maximal new-incident-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(defn additional-tests [app incident-id incident]
  (println "incident id :" incident-id)
  (let [fixed-now (t/internal-now)]
    (helpers/fixture-with-fixed-time
     fixed-now
     (fn []
       (testing "Incident status update: test setup"
         (let [response (PATCH app
                               (str "ctia/incident/" (:short-id incident-id))
                               :body {:incident_time {}}
                               :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 200 (:status response)))))

       (testing "POST /ctia/incident/:id/status Open"
         (let [new-status {:status "Open"}
               response (POST app
                              (str "ctia/incident/" (:short-id incident-id) "/status")
                              :body new-status
                              :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body response)]
           (is (= (:id incident) (:id updated-incident)))
           (is (= 200 (:status response)))
           (is (= "Open" (:status updated-incident)))
           (is (get-in updated-incident [:incident_time :opened]))

           (is (= (get-in updated-incident [:incident_time :opened])
                  (tc/to-date fixed-now)))))

       (testing "POST /ctia/incident/:id/status Closed"
         (let [new-status {:status "Closed"}
               response (POST app
                              (str "ctia/incident/" (:short-id incident-id) "/status")
                              :body new-status
                              :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Closed" (:status updated-incident)))
           (is (get-in updated-incident [:incident_time :closed]))

           (is (= (get-in updated-incident [:incident_time :closed])
                  (tc/to-date fixed-now)))))

       (testing "POST /ctia/incident/:id/status Containment Achieved"
         (let [new-status {:status "Containment Achieved"}
               response (POST app
                              (str "ctia/incident/" (:short-id incident-id) "/status")
                              :body new-status
                              :headers {"Authorization" "45c1f5e3f05d0"})
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Containment Achieved" (:status updated-incident)))
           (is (get-in updated-incident [:incident_time :remediated]))

           (is (= (get-in updated-incident [:incident_time :remediated])
                  (tc/to-date fixed-now)))))
       ))))

(deftest test-incident-crud-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
     (let [parameters (into sut/incident-entity
                            {:app app
                             :patch-tests? true
                             :search-tests? true
                             :example new-incident-maximal
                             :headers {:Authorization "45c1f5e3f05d0"}
                             :additional-tests additional-tests})]
       (entity-crud-test parameters)))))

(defn script-search [app]
  (testing "GET /ctia/incident/search"
    (let [severities (vec vocab/severity) ;; for rand-nth
          incidents-count 10
          incidents (set
                      (repeatedly
                        incidents-count
                        #(-> new-incident-minimal
                             (dissoc :id)
                             (assoc :title (str (gensym)))
                             (assoc :severity (rand-nth severities)))))
          bundle (-> new-bundle-minimal
                     (dissoc :id)
                     (assoc :incidents incidents))
          login (auth/map->Identity {:login  "foouser"
                                     :groups ["foogroup"]})
          created-bundle (bundle/import-bundle
                           bundle
                           nil    ;; external-key-prefixes
                           login
                           (app/service-graph app))
          _ (prn "created bundle:")
          _ ((requiring-resolve 'clojure.pprint/pprint) 
             created-bundle)
          ;; bench revision
          #_#_
          _ (testing ":revision"
              (dotimes [_ 10]
                (doseq [asc? [true false]]
                  (prn "bench revision" (if asc? "asc" "desc"))
                  (let [{:keys [parsed-body]} (time (search-th/search-raw app :incident {:sort_by "revision"
                                                                                         :sort_order (if asc? "asc" "desc")}))
                        _ (prn (count parsed-body))
                        ]))))
          ;; bench severity
          #_#_
          _ (testing ":severity"
              (dotimes [_ 1 #_10]
                (doseq [asc? [true false]]
                  (prn "bench severity" (if asc? "asc" "desc"))
                  (testing {:asc? asc?}
                    (let [{:keys [parsed-body]} (time (search-th/search-raw app :incident {:sort_by "severity"
                                                                                           :sort_order (if asc? "asc" "desc")}))
                          _ (prn (count parsed-body))
                          expected-parsed-body (sort-by :severity #(if asc?
                                                                     (compare %1 %2)
                                                                     (compare %2 %1))
                                                        parsed-body)]
                      (is (= (mapv (juxt :id :severity) expected-parsed-body)
                             (mapv (juxt :id :severity) parsed-body))))))))
          ;; bench severity_int
          _ (testing ":severity_int"
              (dotimes [_ 1 #_10]
                (doseq [asc? [true false]]
                  ;(prn "bench severity_int" (if asc? "asc" "desc"))
                  (testing {:asc? asc?}
                    (let [{:keys [parsed-body] :as raw} (identity #_time (search-th/search-raw app :incident {:sort_by "severity_int"
                                                                                                              :sort_order (if asc? "asc" "desc")}))
                          {:keys [remappings remap-default remap-type]} (-> sut/sort-by-field-exts :severity_int)
                          _ (assert (= :number remap-type))
                          expected-parsed-body (sort-by #(remappings (:severity %) remap-default)
                                                        #(if asc?
                                                           (compare %1 %2)
                                                           (compare %2 %1))
                                                        parsed-body)]
                      (and (is (= incidents-count (count parsed-body)) (pr-str raw))
                           (is (= (mapv :severity expected-parsed-body)
                                  (mapv :severity parsed-body)))
                           (is (= expected-parsed-body
                                  parsed-body))))))))
          _ (run! #(search-th/delete-doc app :incident (:id %))
                  (:incidents created-bundle))])))

(comment
  docker-compose -f containers/dev/m1-docker-compose.yml up
  lein repl
  (do (refresh) (clojure.test/test-vars [(requiring-resolve 'ctia.entity.incident-test/test-incident-script-search)]))
  )
(deftest ^:frenchy64 test-incident-script-search
  (es-helpers/for-each-es-version
    ""
    (cond-> [7]
      (System/getenv "CI") (conj 5))
    #(ductile.index/delete! % "ctia_*")
    (helpers/fixture-ctia-with-app
      (fn [app]
        ;(helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
        ;(whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
        (script-search app)))))

(deftest test-incident-metric-routes
  (test-metric-routes (into sut/incident-entity
                            {:entity-minimal new-incident-minimal
                             :enumerable-fields sut/incident-enumerable-fields
                             :date-fields sut/incident-histogram-fields})))

(deftest test-incident-routes-access-control
  (access-control-test "incident"
                       new-incident-minimal
                       true
                       true
                       test-for-each-store-with-app))
