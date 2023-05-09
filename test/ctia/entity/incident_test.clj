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
            [ctim.examples.bundles :refer [new-bundle-minimal]]
            [ctim.examples.incidents
             :refer
             [new-incident-maximal new-incident-minimal incident-minimal]]
            ductile.index
            [puppetlabs.trapperkeeper.app :as app]
            [schema.core :as s]
            [schema-tools.core :as st]
            [java-time.api :as jt]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(deftest incident-scores-schema-test
  (let [get-in-config (partial get-in {:ctia {:http {:incident {:score-types "global,ttp,asset"}}}})
        fake-services {:ConfigService {:get-in-config get-in-config}}
        expected (st/optional-keys {:global s/Num :ttp s/Num :asset s/Num})
        res (sut/mk-scores-schema fake-services)]
    (is (= expected res))
    (is (= {} (sut/mk-scores-schema {:ConfigService {:get-in-config (constantly nil)}})))))

(defn post-status [app uri-encoded-id new-status]
  (POST app
        (str "ctia/incident/" uri-encoded-id "/status")
        :body {:status new-status}
        :headers {"Authorization" "45c1f5e3f05d0"}))

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
         (let [new-status "Open"
               response (post-status app (:short-id incident-id) new-status)
               updated-incident (:parsed-body response)]
           (is (= (:id incident) (:id updated-incident)))
           (is (= 200 (:status response)))
           (is (= "Open" (:status updated-incident)))
           (is (get-in updated-incident [:incident_time :opened]))

           (is (= (get-in updated-incident [:incident_time :opened])
                  (tc/to-date fixed-now)))))

       (testing "POST /ctia/incident/:id/status Closed"
         (let [new-status "Closed"
               response (post-status app (:short-id incident-id) new-status)
               updated-incident (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= "Closed" (:status updated-incident)))
           (is (get-in updated-incident [:incident_time :closed]))

           (is (= (get-in updated-incident [:incident_time :closed])
                  (tc/to-date fixed-now)))))

       (testing "POST /ctia/incident/:id/status Containment Achieved"
         (let [new-status "Containment Achieved"
               response (post-status app (:short-id incident-id) new-status)
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
     (let [parameters (assoc sut/incident-entity
                             :app app
                             :patch-tests? true
                             :search-tests? true
                             :example new-incident-maximal
                             :headers {:Authorization "45c1f5e3f05d0"}
                             :additional-tests additional-tests)]
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
    #(ductile.index/delete! % "ctia_*")
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
    [5 7]
    #(ductile.index/delete! % "ctia_*")
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

(deftest filter-incidents-by-scores-range
  (es-helpers/for-each-es-version
    "filter by scores"
    [7]
    #(ductile.index/delete! % "ctia_*")
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
(assert (every? incident-statuses ["New" "Open" "Closed" "Rejected"]))

#_ ;;FIXME
(deftest compute-intervals-test
  ;; tests the laziness of the second argument of sut/compute-intervals. since it's expensive
  ;; to compute, it's important we don't realize it unnecessarily.
  (testing "retrieves full incident by need"
    (let [irrelevant-statuses (disj incident-statuses "Closed" "Open")]
      (doseq [incident-update (mapv #(do {:id "foo" :status %})
                                    (shuffle irrelevant-statuses))]
        (testing (pr-str incident-update)
          (let [dly (delay
                      ;; should be a stored incident, but it doesn't really make a practical difference
                      ;; since we're testing that this delay is never realized.
                      incident-minimal)]
            (is (= incident-update (sut/compute-intervals incident-update dly)))
            (is (not (realized? dly)) "Store incident was retrieved unnecessarily"))))))
  ;; the interesting cases where intervals are potentially computed. these are suprisingly involved
  ;; since the "earlier" time is in the stored incident, and the "later" time is in the incident update,
  ;; and the same entries can appear in both but only one is relevant.
  ;; there are many invariants to juggle in these tests. to increase certainty that we're actually
  ;; testing the invariants we claim to, we use local functions to construct the test cases. Unfortunately,
  ;; this makes the code hard to follow. Printing the cases might make for a clearer view.
  (let [earlier (-> (jt/instant 0) (jt/plus (jt/seconds -10)) jt/java-date)
        later   (-> (jt/instant 0) (jt/plus (jt/seconds  10)) jt/java-date)
        computed-interval 20
        _ (assert (= computed-interval
                     (jt/time-between (jt/instant earlier) (jt/instant later) :seconds)))
        precomputed-interval 15 ;; for testing that old intervals are preserved
        _ (assert (not= computed-interval precomputed-interval))]
    (testing "updating status 'Open'"
      (doseq [{:keys [id incident-update stored-incident expected]}
              (let [->base-test-case (s/fn [prev-status :- (st/get-in sut/Incident [:status])
                                            earlier :- s/Inst
                                            later :- s/Inst]
                                       (let [incident-update {:id "foo" :status "Open" :incident_time {:opened later}}]
                                         {:incident-update incident-update 
                                          :stored-incident (-> incident-minimal
                                                               ;; Note: :incident_time.opened is included in incident-minimal
                                                               ;; due to the Incident schema.
                                                               ;; The test :no-update-because-earlier-after-later is sufficient
                                                               ;; to show that this is ignored by sut/compute-intervals,
                                                               ;; and only the :incident_time.opened in :incident-update is used
                                                               ;; to calculate the interval.
                                                               (assoc :created earlier
                                                                      :status prev-status)
                                                               (dissoc :intervals))
                                          ;; default to no interval being computed, override for success cases
                                          :expected incident-update}))]
                (concat
                  (let [base (->base-test-case "New" earlier later)]
                    (concat
                      [;; if :stored-incident does not already have a :new_to_opened interval, compute interval and include in update
                       (-> base
                           (assoc :id :update-new_to_opened)
                           (assoc-in [:expected :intervals :new_to_opened] computed-interval))
                       ;; if :stored-incident already has a :new_to_opened interval, elide interval from update
                       (-> base
                           (assoc :id :new_to_opened-already-exists)
                           (assoc-in [:stored-incident :intervals :new_to_opened] precomputed-interval))]
                      ;; if previous status is not New, then don't update
                      (doto (map #(-> base
                                      (assoc :id (str "no-update-wrong-prev-status " %))
                                      (assoc-in [:stored-incident :status] %))
                                 (shuffle (disj incident-statuses "New")))
                        (-> seq assert))))
                  [;; if :created is after the updated :incident_time.opened, elide interval from update
                   (-> (->base-test-case "New" later earlier)
                       (assoc :id :no-update-because-earlier-after-later))]))]
        (testing (pr-str id)
          (is (= expected (sut/compute-intervals incident-update (delay stored-incident)))))))
    (testing "updating status 'Closed'"
      (doseq [;; stored status doesn't matter
              stored-status (shuffle incident-statuses)
              {:keys [id incident-update stored-incident expected]}
              (let [->base-test-case (s/fn [earlier :- s/Inst
                                            later :- s/Inst]
                                       (let [incident-update {:id "foo" :status "Closed" :incident_time {:closed later}}]
                                         {:incident-update incident-update
                                          :stored-incident (-> incident-minimal
                                                               (assoc-in [:incident_time :opened] earlier)
                                                               (assoc :status stored-status)
                                                               (dissoc :intervals))
                                          ;; default to no interval being computed, override for success cases
                                          :expected incident-update}))]
                (concat
                  (let [base (->base-test-case earlier later)]
                    [(-> base
                         (assoc :id :update-opened_to_closed)
                         ;; if :stored-incident does not already have an :opened_to_closed interval, compute it
                         (assoc-in [:expected :intervals :opened_to_closed] computed-interval))
                     (-> base
                         (assoc :id :opened_to_closed-already-exists)
                         ;; if :stored-incident already has an :opened_to_closed interval, don't add it to the update
                         (assoc-in [:stored-incident :intervals :opened_to_closed] precomputed-interval))])
                  [;; if :incident_time.opened is after the updated :incident_time.closed, elide interval from update
                   (-> (->base-test-case later earlier)
                       (assoc :id :no-update-because-earlier-after-later))]))]
        (testing (pr-str id " " stored-status)
          (is (= expected (sut/compute-intervals incident-update (delay stored-incident)))))))))

#_ ;;FIXME
(deftest incident-average-metrics-test
  (test-for-each-store-with-app
    (fn [app]
      (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
      (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
      (try (let [incident->intervals [[(assoc (gen-new-incident) :status "New" :title "incident1")
                                       {:new_to_opened 100
                                        :opened_to_closed 150}]
                                      [(assoc (gen-new-incident) :status "New" :title "incident2")
                                       {:new_to_opened 200
                                        :opened_to_closed 250}]
                                      [(assoc (gen-new-incident) :status "New" :title "incident3")
                                       {:new_to_opened 500
                                        :opened_to_closed 550}]]
                 expected-new_to_opened 266
                 _ (assert (= expected-new_to_opened
                              (long (/ (apply + (map (comp :new_to_opened second)
                                                     incident->intervals))
                                       (count incident->intervals)))))
                 expected-opened_to_closed 316
                 _ (assert (= expected-opened_to_closed
                              (long (/ (apply + (map (comp :opened_to_closed second)
                                                     incident->intervals))
                                       (count incident->intervals)))))
                 new-time (jt/instant)
                 bulk-out (helpers/fixture-with-fixed-time
                            (jt/java-date new-time)
                            #(create-incidents app (into #{} (map first) incident->intervals)))
                 incident-id->incident+intervals (map vector
                                                      (map :id (:results bulk-out))
                                                      incident->intervals)
                 _ (assert (= (count incident-id->incident+intervals) (count incident->intervals))
                           bulk-out)
                 _ (testing "populate intervals"
                     (doseq [[incident-id [incident {:keys [new_to_opened opened_to_closed]}]] incident-id->incident+intervals
                             [new-status next-time] [["Open" (jt/plus new-time (jt/seconds new_to_opened))]
                                                     ["Closed" (jt/plus new-time (jt/seconds (+ new_to_opened opened_to_closed)))]]]
                       (testing (pr-str [new-status incident])
                         (let [{:keys [parsed-body] :as response} (helpers/fixture-with-fixed-time
                                                                    (jt/java-date next-time)
                                                                    #(post-status app (uri/uri-encode incident-id) new-status))]
                           (is (= 200 (:status response))
                               response)))))]
             (testing "average aggregation"
               (doseq [[field expected] [["new_to_opened" expected-new_to_opened]
                                         ["opened_to_closed" expected-opened_to_closed]]]
                 (testing field
                   (let [{:keys [parsed-body] :as raw} (GET app "ctia/incident/metric/average"
                                                            :headers {"Authorization" "45c1f5e3f05d0"}
                                                            :query-params {:aggregate-on (str "intervals." field)
                                                                           :from new-time})]
                     (and (is (= 200 (:status raw)) (pr-str raw))
                          (is (= expected parsed-body)
                              (pr-str parsed-body))))))))
           (finally (purge-incidents! app))))))
