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
             [new-incident-maximal new-incident-minimal]]
            [schema.core :as s]))

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
   "Info" 1
   "Low" 2
   "Medium" 3
   "High" 4
   "Critical" 5})

(defn gen-new-incident [severity]
  (-> new-incident-minimal
      (dissoc :id)
      (assoc :title (str (java.util.UUID/randomUUID)))
      (assoc :revision (doto (ctim-severity-order severity)
                         assert))
      (assoc :severity severity)))

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
  ([] (severity-int-script-search {}))
  ([{:keys [bench-atom]}]
   (es-helpers/for-each-es-version
     ""
     (cond-> [7]
       (System/getenv "CI") (conj 5))
     #(ductile.index/delete! % "ctia_*")
     (helpers/with-properties ["ctia.store.es.default.auth" es-helpers/basic-auth]
       (helpers/fixture-ctia-with-app
         (fn [app]
           (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
           (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
           (doseq [;; only one ordering with these severities. don't add both Unknown and None in the same test.
                   canonical-fixed-severities-asc [["Unknown" "Info"]
                                                   ["Unknown" "Critical"]
                                                   ["None" "Info"]
                                                   ["None" "Critical"]
                                                   ["Info" "Low" "Medium" "High" "Critical"]
                                                   ["Unknown" "Info" "Low" "Medium" "High" "Critical"]
                                                   ["None" "Info" "Low" "Medium" "High" "Critical"]]
                   ;; scale up the test size by repeating elements
                   multiplier (cond-> [1]
                                bench-atom (into [10 100 1000]))
                   :let [fixed-severities-asc (into [] (mapcat #(repeat multiplier %))
                                                    canonical-fixed-severities-asc)]]
             (try (testing (pr-str fixed-severities-asc)
                    (let [incidents-count (count fixed-severities-asc)
                          incidents (into (sorted-set-by #(compare (:title %1) (:title %2))) ;; a (possibly vain) attempt to randomize the order in which ES will index
                                          (map gen-new-incident)
                                          fixed-severities-asc)
                          _ (assert (= incidents-count (count incidents))
                                    (format "case: %s, multiplier %s, expected incidents: %s, actual:"
                                            canonical-fixed-severities-asc
                                            multiplier
                                            incidents-count
                                            (count incidents)))
                          created-bundle (create-incidents app incidents)
                          _ (doseq [sort_by (cond-> ["severity_int"]
                                              ;; hijacking this int field for perf comparison, see `gen-new-incident`
                                              bench-atom (conj "revision"))
                                    asc? [true false]]
                              (dotimes [i (if bench-atom 1 1)]
                                (testing {:sort_by sort_by :asc? asc?}
                                  (let [[{:keys [parsed-body] :as raw} ms-time] (result+ms-time
                                                                                  (search-th/search-raw app :incident {:sort_by sort_by
                                                                                                                       :sort_order (if asc? "asc" "desc")}))
                                        expected-parsed-body (sort-by (fn [{:keys [severity]}]
                                                                        {:post [(number? %)]}
                                                                        (ctim-severity-order severity))
                                                                      #(if asc?
                                                                         (compare %1 %2)
                                                                         (compare %2 %1))
                                                                      parsed-body)
                                        success? (and (is (= incidents-count (count expected-parsed-body)) (pr-str raw))
                                                      (is (= incidents-count (count parsed-body)) (pr-str raw))
                                                      ;; avoid potential bugs via sort-by by using fixed-severities-asc directly
                                                      (is (= ((if asc? identity rseq) fixed-severities-asc)
                                                             (mapv :severity parsed-body)))
                                                      ;; should succeed even with multipliers because sort-by is stable
                                                      (is (= expected-parsed-body
                                                             parsed-body)))]
                                    (when bench-atom
                                      (assert success?)
                                      (swap! bench-atom update-in [canonical-fixed-severities-asc multiplier sort_by]
                                             (fn [prev]
                                               (let [nxt (-> prev
                                                             (update :incidents-count #(or (when %
                                                                                             (assert (= incidents-count (:incidents-count %))
                                                                                                     (format "case: %s, multiplier %s, expected incidents: %s, actual:"
                                                                                                             canonical-fixed-severities-asc
                                                                                                             multiplier
                                                                                                             incidents-count
                                                                                                             (count incidents)))
                                                                                             %)
                                                                                           incidents-count))
                                                             (update :ms-times (fnil conj []) ms-time)
                                                             ((fn [{:keys [ms-times] :as res}]
                                                                (assoc res :ms-avg (format "%e" (double (/ (apply + ms-times) (count ms-times))))))))]
                                                 ;; dirty side effects in swap!. note: atom access is seralized for now
                                                 (println)
                                                 (println (format "Benchmark %s" sort_by))
                                                 (println (format "Case: %s %s (%sth iteration)"
                                                                  (pr-str canonical-fixed-severities-asc)
                                                                  (if asc? "ascending" "descending")
                                                                  (str i)))
                                                 (println (format "Multiplier: %s" (str multiplier)))
                                                 (println (format "Duration: %ems" ms-time))
                                                 (println (format "Average: %sms" (:ms-avg nxt)))))))))))]))
                  (finally (purge-incidents! app))))))))))

(comment
  docker-compose -f containers/dev/m1-docker-compose.yml up
  lein repl
  (do (refresh) (clojure.test/test-vars [(requiring-resolve 'ctia.entity.incident-test/test-incident-severity-int-search)]))
  )
(deftest test-incident-severity-int-search
  (severity-int-script-search))

(deftest ^:disabled bench-incident-severity-int-search
  (let [results (atom {})]
    (severity-int-script-search
      {:bench-atom results})
    ((requiring-resolve 'clojure.pprint/pprint) @results)))

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
