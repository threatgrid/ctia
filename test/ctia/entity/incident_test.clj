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

(defn gen-new-incident [severity]
  (-> new-incident-minimal
      (assoc :id (str "transient:" (java.util.UUID/randomUUID)))
      (assoc :title (str (gensym)))
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

(def ctim-severity-order
  "As opposed to ES level, which is lowercase"
  {"Unknown" 0
   "None" 0
   "Info" 1
   "Low" 2
   "Medium" 3
   "High" 4
   "Critical" 5})

(defn purge-incidents! [app]
  (search-th/delete-search app :incident {:query "*"
                                          :REALLY_DELETE_ALL_THESE_ENTITIES true}))

(defn severity-int-script-search [app]
  (let [fixed-severities-asc ["Info" "Low" "Medium" "High" "Critical"]
        incidents-count (count fixed-severities-asc)
        incidents (into (sorted-set-by #(compare (:id %1) (:id %2))) ;; a (possibly vain) attempt to randomize the order in which ES will index
                        (map gen-new-incident)
                        fixed-severities-asc)
        _ (assert (= (count incidents) incidents-count))
        created-bundle (create-incidents app incidents)
        _ (doseq [asc? [true false]]
            (testing {:asc? asc?}
              (let [{:keys [parsed-body] :as raw} (search-th/search-raw app :incident {:sort_by "severity_int"
                                                                                       :sort_order (if asc? "asc" "desc")})
                    expected-parsed-body (sort-by (fn [{:keys [severity]}]
                                                    {:post [(number? %)]}
                                                    (ctim-severity-order severity))
                                                  #(if asc?
                                                     (compare %1 %2)
                                                     (compare %2 %1))
                                                  parsed-body)]
                (and (is (= incidents-count (count parsed-body)) (pr-str raw))
                     (is (= ((if asc? identity rseq) fixed-severities-asc)
                            (mapv :severity parsed-body)))
                     (is (= expected-parsed-body
                            parsed-body))))))
        _ (purge-incidents! app)]))

(comment
  docker-compose -f containers/dev/m1-docker-compose.yml up
  lein repl
  (do (refresh) (clojure.test/test-vars [(requiring-resolve 'ctia.entity.incident-test/test-incident-script-search)]))
  )
(deftest ^:frenchy64 test-incident-severity-int-search
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
          (severity-int-script-search app))))))

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
