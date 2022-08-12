(ns ctia.entity.incident-test
  (:require [clj-momo.lib.clj-time.coerce :as tc]
            [clj-momo.lib.clj-time.core :as t]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.auth.threatgrid :as auth]
            [ctia.bundle.core :as bundle]
            [ctia.entity.incident :as sut]
            [ctia.test-helpers.access-control :refer [access-control-test]]
            [ctia.test-helpers.aggregate :refer [test-metric-routes]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers :refer [PATCH POST]]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.search :as search-th]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.examples.bundles :refer [new-bundle-minimal]]
            [ctim.examples.incidents
             :refer
             [new-incident-maximal new-incident-minimal]]
            ductile.index
            [puppetlabs.trapperkeeper.app :as app]
            [schema.core :as s]
            [java-time :as jt]))

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
                  (tc/to-date fixed-now)))))))))

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
                                          :REALLY_DELETE_ALL_THESE_ENTITIES true}))

;; extracted from the much more thorough severity-int-script-search
(deftest simple-severity-int-script-search-test
  (es-helpers/for-each-es-version
    "severity sorts like #'ctim-severity-order"
    [5 7]
    #(ductile.index/delete! % "ctia_*")
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
                                                                    (format "severity:%1$s,created:%1$s"
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
    [5 7]
    #(ductile.index/delete! % "ctia_*")
    (helpers/with-properties (into ["ctia.auth.type" "allow-all"]
                                   es-helpers/basic-auth-properties)
      (helpers/fixture-ctia-with-app
        (fn [app]
          ;(helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
          ;(whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
          (try (let [ascending-incidents [(assoc (gen-new-incident) :tactics ["bad-id"])
                                          (assoc (gen-new-incident) :tactics ["TA0043"])
                                          (assoc (gen-new-incident) :tactics ["TA0042"])
                                          ;; same position as above
                                          ;(assoc (gen-new-incident) :tactics ["TA0043" "TA0042"])
                                          (assoc (gen-new-incident) :tactics ["TA0043" "TA0001"])
                                          (assoc (gen-new-incident) :tactics ["TA0002" "TA0043"])
                                          (assoc (gen-new-incident) :tactics ["bad-id" "TA0003"])]]
                 (create-incidents app (shuffle ascending-incidents))
                 (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:sort_by "tactics"})]
                   (and (is (= 200 (:status raw)) (pr-str raw))
                        (is (= ascending-incidents parsed-body) (pr-str raw)))))
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
     [5 7]
     #(ductile.index/delete! % "ctia_*")
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
                                      [{:keys [parsed-body] :as raw} ms-time] (result+ms-time
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
                             :date-fields sut/incident-histogram-fields})))

(deftest test-incident-routes-access-control
  (access-control-test "incident"
                       new-incident-minimal
                       true
                       true
                       test-for-each-store-with-app))
