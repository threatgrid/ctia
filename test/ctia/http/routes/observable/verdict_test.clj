(ns ctia.http.routes.observable.verdict-test
  (:require [clj-momo.lib.time :as time]
            [clj-momo.test-helpers.core :as mht]
            [clj-time.core :as clj-time]
            [clj-time.coerce :as time-coerce]
            [clj-time.format :as time-format]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.store :as store]
            [ctia.test-helpers
             [es :as es-helpers]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [fixture-ctia-with-app DELETE GET POST]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.domain.id :as id]))

(use-fixtures :once (join-fixtures [mht/fixture-schema-validation
                                    helpers/fixture-properties:events-enabled
                                    whoami-helpers/fixture-server]))

(deftest test-observable-verdict-route
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (testing "test setup: create a judgement (1)"
       ;; Incorrect observable
       (let [response (POST app
                            "ctia/judgement"
                            :body {:observable {:value "127.0.0.1"
                                                :type "ip"}
                                   :disposition 1
                                   :source "test"
                                   :priority 100
                                   :severity "High"
                                   :confidence "Low"
                                   :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                            :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 (:status response)))))

     (testing "test setup: create a judgement (2)"
       ;; Lower priority
       (let [response (POST app
                            "ctia/judgement"
                            :body {:observable {:value "10.0.0.1"
                                                :type "ip"}
                                   :disposition 1
                                   :source "test"
                                   :priority 90
                                   :severity "High"
                                   :confidence "Low"
                                   :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                            :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 (:status response)))))

     (testing "test setup: create a judgement (3)"
       ;; Wrong disposition
       (let [response (POST app
                            "ctia/judgement"
                            :body {:observable {:value "10.0.0.1"
                                                :type "ip"}
                                   :disposition 3
                                   :source "test"
                                   :priority 99
                                   :severity "High"
                                   :confidence "Low"
                                   :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                            :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 (:status response)))))


     (testing "a verdict that doesn't exist is a 404"
       (let [{status :status
              parsed-body :body}
             (GET app
                  "ctia/ip/10.0.0.42/verdict"
                  :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 404 status))
         (is (= "{:message \"no verdict currently available for the supplied observable\"}"
                parsed-body))))

     (testing "test setup: create a judgement (4)"
       ;; Loses a tie because of its timestamp being later
       (let [response (POST app
                            "ctia/judgement"
                            :body {:observable {:value "10.0.0.1"
                                                :type "ip"}
                                   :disposition 2
                                   :source "test"
                                   :priority 99
                                   :severity "High"
                                   :confidence "Low"
                                   :valid_time {:start_time "2016-02-12T00:01:00.000-00:00"}}
                            :headers {"Authorization" "45c1f5e3f05d0"})
             judgement-1 (:parsed-body response)]
         (is (= 201 (:status response)))))

     (testing "with a highest-priority judgement"
       (let [{status :status
              judgement :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:value "10.0.0.1"
                                       :type "ip"}
                          :disposition 2
                          :source "test"
                          :priority 99
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})

             judgment-id
             (id/long-id->id (:id judgement))]
         (is (= 201 status)) ;; success creating judgement

         (testing "GET /ctia/:observable_type/:observable_value/verdict"
           (let [{status :status
                  verdict :parsed-body}
                 (GET app
                      "ctia/ip/10.0.0.1/verdict"
                      :headers {"Authorization" "45c1f5e3f05d0"})]
             (is (= 200 status))
             (is (= {:type "verdict"
                     :disposition 2
                     :disposition_name "Malicious"
                     :judgement_id (:id judgement)
                     :observable {:value "10.0.0.1", :type "ip"}
                     :valid_time {:start_time #inst "2016-02-12T00:00:00.000-00:00",
                                  :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
                    verdict)))))))))


(deftest test-observable-verdict-route-2
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     ;; This test case catches a bug that was in the atom store
     ;; It tests the code path where priority is equal but dispositions differ
     (testing "test setup: create a judgement (1)"
       (let [{status :status}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:value "string",
                                       :type "device"},
                          :reason_uri "string",
                          :source "string",
                          :disposition 1,
                          :reason "string",
                          :source_uri "string",
                          :priority 99,
                          :severity "Low"
                          :valid_time {:start_time "2016-02-12T14:56:26.719-00:00"
                                       :end_time "2016-02-12T14:56:26.814-00:00"}
                          :confidence "Medium"}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))))

     (testing "with a verdict judgement"
       (let [{status :status
              judgement :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:value "10.0.0.1",
                                       :type "ip"},
                          :reason_uri "string",
                          :source "string",
                          :disposition 2,
                          :reason "string",
                          :source_uri "string",
                          :priority 99,
                          :severity "Low"
                          :valid_time {:start_time "2016-02-12T14:56:26.814-00:00"}
                          :confidence "Medium"}
                   :headers {"Authorization" "45c1f5e3f05d0"})

             judgement-id
             (id/long-id->id (:id judgement))]
         (is (= 201 status))

         (testing "GET /ctia/:observable_type/:observable_value/verdict"
           (with-redefs [time/now (constantly (time/timestamp "2016-02-12T15:42:58.232-00:00"))]
             (let [{status :status
                    verdict :parsed-body}
                   (GET app
                        "ctia/ip/10.0.0.1/verdict"
                        :headers {"Authorization" "45c1f5e3f05d0"})]
               (is (= 200 status))
               (is (= {:observable {:value "10.0.0.1",:type "ip"}
                       :type "verdict"
                       :disposition 2
                       :disposition_name "Malicious"
                       :judgement_id (:id judgement)
                       :valid_time {:start_time #inst "2016-02-12T14:56:26.814-00:00",
                                    :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
                      verdict))))))))))


(deftest test-observable-verdict-route-when-judgement-deleted
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "test setup: create judgement-1"
       (let [{status :status
              judgement-1 :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:value "10.0.0.1"
                                       :type "ip"}
                          :external_ids ["judgement-1"]
                          :disposition 1
                          :source "test"
                          :priority 100
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})

             judgement-1-id
             (some-> (:id judgement-1) id/long-id->id)]
         (is (= 201 status))

         (testing "test setup: delete judgement-1"
           (let [{status :status}
                 (DELETE app
                         (str "ctia/judgement/" (:short-id judgement-1-id))
                         :headers {"Authorization" "45c1f5e3f05d0"})]
             (is (= 204 status))))

         (testing "GET /ctia/:observable_type/:observable_value/verdict"
           (let [{status :status}
                 (GET app
                      "ctia/ip/10.0.0.1/verdict"
                      :headers {"Authorization" "45c1f5e3f05d0"})]
             (is (= 404 status))))))

     (testing "test setup: create judgement-2"
       (let [{status :status
              judgement-2 :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:value "10.0.0.1"
                                       :type "ip"}
                          :external_ids ["judgement-2"]
                          :disposition 1
                          :source "test"
                          :priority 100
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})

             judgement-2-id
             (some-> (:id judgement-2) id/long-id->id)]
         (is (= 201 status))

         (testing "test setup: create judgement-3"
           (let [{status :status
                  judgement-3 :parsed-body}
                 (POST app
                       "ctia/judgement"
                       :body {:observable {:value "10.0.0.1"
                                           :type "ip"}
                              :external_ids ["judgement-3"]
                              :disposition 1
                              :source "test"
                              :priority 100
                              :severity "High"
                              :confidence "Low"
                              :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                       :headers {"Authorization" "45c1f5e3f05d0"})

                 judgement-3-id
                 (some-> (:id judgement-3) id/long-id->id)]
             (is (= 201 status))

             (testing "test steup: delete judgement-3"
               (let [{status :status}
                     (DELETE app
                             (str "ctia/judgement/" (:short-id judgement-3-id))
                             :headers {"Authorization" "45c1f5e3f05d0"})]
                 (is (= 204 status))))))

         (testing "GET /ctia/:observable_type/:observable_value/verdict"
           (let [{status :status
                  verdict :parsed-body}
                 (GET app
                      "ctia/ip/10.0.0.1/verdict"
                      :headers {"Authorization" "45c1f5e3f05d0"})]
             (is (= 200 status))
             (is (= {:type "verdict"
                     :disposition 1
                     :disposition_name "Clean"
                     :judgement_id (:id judgement-2)
                     :observable {:value "10.0.0.1", :type "ip"}
                     :valid_time {:start_time #inst "2016-02-12T00:00:00.000-00:00"
                                  :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
                    verdict)))))))))

(deftest test-observable-verdict-with-different-valid-times
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing ":start_time is now and :end_time is in 2 weeks"

       (let [{:keys [type value]
              :as observable}
             {:type "sha256"
              :value (str "39091a6e0d00472273c3d644a47611b"
                          "ac95554d8d48899ec74d1b3127542f89b")}

             {status :status
              judgement :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:valid_time {:start_time (-> (time/now)
                                                       time/format-date-time)
                                       :end_time (-> (time/plus-n :weeks (time/now) 2)
                                                     time/format-date-time)}
                          :observable observable
                          :reason_uri "https://example.com/",
                          :source "Example",
                          :external_ids ["judgement-1"],
                          :disposition 2,
                          :disposition_name "Malicious"
                          :reason "Example judgement",
                          :source_uri "https://example.com/",
                          :priority 0,
                          :severity "None",
                          :tlp "green",
                          :confidence "None"}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))

         (testing "GET /ctia/:observable_type/:observable_value/verdict"
           (let [{status :status
                  verdict :parsed-body}
                 (GET app
                      (str "ctia/" type "/" value "/verdict")
                      :headers {"Authorization" "45c1f5e3f05d0"})]
             (is (= 200 status))
             (is (= (:id judgement)
                    (:judgement_id verdict)))))))

     (testing ":start_time and :end_time are the same (now)"
       (let [{status :status
              judgement :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:valid_time {:start_time (-> (time/now)
                                                       time/format-date-time)
                                       :end_time (-> (time/now)
                                                     time/format-date-time)}
                          :observable {:value "10.0.0.1"
                                       :type "ip"}
                          :reason_uri "https://example.com/",
                          :source "Example",
                          :external_ids ["judgement-2"],
                          :disposition 2,
                          :disposition_name "Malicious"
                          :reason "Example judgement",
                          :source_uri "https://example.com/",
                          :priority 0,
                          :severity "None",
                          :tlp "green",
                          :confidence "None"}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))

         (testing "GET /ctia/:observable_type/:observable_value/verdict"
           (let [{status :status
                  verdict :parsed-body}
                 (GET app
                      "ctia/ip/10.0.0.1/verdict"
                      :headers {"Authorization" "45c1f5e3f05d0"})]
             (is (= 404 status))))))

     (testing ":end_time is today, but in the future"
       (let [format (partial time-format/unparse (time-format/formatters :date-time))

             {:keys [type value]
              :as observable}
             {:type "ip"
              :value "10.0.0.2"}

             {status :status
              judgement :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:valid_time {:start_time (-> (clj-time/now)
                                                       (clj-time/minus
                                                        (clj-time/minutes 10))
                                                       format)
                                       :end_time (-> (clj-time/now)
                                                     (clj-time/plus
                                                      (clj-time/seconds 10))
                                                     format)}
                          :observable observable
                          :reason_uri "https://example.com/",
                          :source "Example",
                          :external_ids ["judgement-3"],
                          :disposition 2,
                          :disposition_name "Malicious"
                          :reason "Example judgement",
                          :source_uri "https://example.com/",
                          :priority 0,
                          :severity "None",
                          :tlp "green",
                          :confidence "None"}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))

         (testing "GET /ctia/:observable_type/:observable_value/verdict"
           (let [{status :status
                  verdict :parsed-body}
                 (GET app
                      (str "ctia/" type "/" value "/verdict")
                      :headers {"Authorization" "45c1f5e3f05d0"})]
             (is (= 200 status))
             (is (= (:id judgement)
                    (:judgement_id verdict)))))))

     (testing ":start_time and :end_time are both in the future"
       (let [format (partial time-format/unparse (time-format/formatters :date-time))

             {:keys [type value]
              :as observable}
             {:type "ip"
              :value "10.0.0.3"}

             {status :status
              judgement :parsed-body}
             (POST app
                   "ctia/judgement"
                   :body {:valid_time {:start_time (-> (clj-time/now)
                                                       (clj-time/plus
                                                        (clj-time/minutes 1))
                                                       format)
                                       :end_time (-> (clj-time/now)
                                                     (clj-time/plus
                                                      (clj-time/minutes 2))
                                                     format)}
                          :observable observable
                          :reason_uri "https://example.com/",
                          :source "Example",
                          :external_ids ["judgement-4"],
                          :disposition 2,
                          :disposition_name "Malicious"
                          :reason "Example judgement",
                          :source_uri "https://example.com/",
                          :priority 0,
                          :severity "None",
                          :tlp "green",
                          :confidence "None"}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))

         (testing "GET /ctia/:observable_type/:observable_value/verdict"
           (let [{status :status
                  verdict :parsed-body}
                 (GET app
                      (str "ctia/" type "/" value "/verdict")
                      :headers {"Authorization" "45c1f5e3f05d0"})]
             (is (= 404 status)))))))))

(deftest test-observable-verdict-access-control
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (helpers/set-capabilities! app "baruser" ["bargroup"] "user" all-capabilities)
     (helpers/set-capabilities! app "foobaruser" ["bargroup"] "user" all-capabilities)

     (whoami-helpers/set-whoami-response app
                                         "foouser"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (whoami-helpers/set-whoami-response app
                                         "baruser"
                                         "baruser"
                                         "bargroup"
                                         "user")

     (whoami-helpers/set-whoami-response app
                                         "foobaruser"
                                         "foobaruser"
                                         "bargroup"
                                         "user")

     (testing "verdict route TLP behavior"
       (let [green-observable
             {:type "domain"
              :value "green.com"}
             amber-observable
             {:type "domain"
              :value "amber.com"}
             red-observable
             {:type "domain"
              :value "red.com"}
             base-judgement
             {:valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}
              :observable green-observable
              :reason_uri "https://example.com/",
              :source "Example",
              :disposition 2,
              :disposition_name "Malicious"
              :reason "Example judgement",
              :source_uri "https://example.com/",
              :priority 0,
              :severity "None",
              :tlp "green",
              :confidence "None"}
             green-judgement-post
             (POST app
                   "ctia/judgement"
                   :body (assoc base-judgement
                                :observable green-observable
                                :tlp "green")
                   :headers {"Authorization" "foouser"})
             amber-judgement-post
             (POST app
                   "ctia/judgement"
                   :body (assoc base-judgement
                                :observable amber-observable
                                :tlp "amber")
                   :headers {"Authorization" "baruser"})
             red-judgement-post
             (POST app
                   "ctia/judgement"
                   :body (assoc base-judgement
                                :observable red-observable
                                :tlp "red")
                   :headers {"Authorization" "foobaruser"})]

         (is (= 201 (:status green-judgement-post)))

         ;; XFV-20: a verdict is a tenant-local trust decision. Pre-fix, a green
         ;; Judgement produced a verdict "readable by everyone" -- with the
         ;; default `max-record-visibility=everyone`, the read filter's public-TLP
         ;; clause let any green/white record contribute to any org's verdict.
         ;; That path was itself a cross-tenant poisoning vector: an attacker
         ;; could set `tlp=green` (visible to all by default) on a high-priority
         ;; Clean judgement and dominate every tenant's verdict -- the same abuse
         ;; XFV-20 closes for `authorized_groups`, and NOT closable by only
         ;; dropping the authorized_* clauses. `list-active-by-observable` now
         ;; ANDs a mandatory owner-org filter, so a verdict is computed only from
         ;; the caller's own org's judgements, regardless of TLP. This matches the
         ;; behavior `test-observable-verdict-access-control-max-record-visibility`
         ;; already asserts under `max-record-visibility=group` -- the verdict path
         ;; is now tenant-local unconditionally.
         ;;
         ;; Cross-tenant sharing on reads/lists is UNCHANGED: baruser/foobaruser
         ;; can still read the green judgement directly (asserted below); only the
         ;; aggregated verdict is scoped to the querying org.
         (testing "a green Judgement contributes to the owning org's verdict only"
           (let [green-judgement-id (get-in green-judgement-post [:parsed-body :id])
                 green-judgement-short-id (some-> green-judgement-id id/long-id->id :short-id)
                 {status-1 :status
                  verdict-1 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type green-observable)
                           "/" (:value green-observable)
                           "/verdict")
                      :headers {"Authorization" "foouser"})
                 {status-2 :status}
                 (GET app
                      (str "ctia/"
                           (:type green-observable)
                           "/"
                           (:value green-observable)
                           "/verdict")
                      :headers {"Authorization" "baruser"})
                 {status-3 :status}
                 (GET app
                      (str "ctia/"
                           (:type green-observable)
                           "/"
                           (:value green-observable)
                           "/verdict")
                      :headers {"Authorization" "foobaruser"})]

             ;; the owning org (foogroup) still gets its own judgement's verdict
             (is (= 200 status-1))
             (is (= green-judgement-id
                    (:judgement_id verdict-1)))

             ;; other orgs no longer inherit the green judgement in THEIR verdict
             (is (= 404 status-2)
                 "a green judgement owned by another org must not contribute to the caller's verdict")
             (is (= 404 status-3)
                 "a green judgement owned by another org must not contribute to the caller's verdict")

             ;; but cross-tenant READ of the green judgement is unchanged: the
             ;; guard is scoped to the verdict path, not to reads/lists.
             (is (= 200 (:status (GET app
                                      (str "ctia/judgement/" green-judgement-short-id)
                                      :headers {"Authorization" "baruser"})))
                 "the green judgement itself remains readable cross-tenant (only the verdict is scoped)")
             (is (= 200 (:status (GET app
                                      (str "ctia/judgement/" green-judgement-short-id)
                                      :headers {"Authorization" "foobaruser"})))
                 "the green judgement itself remains readable cross-tenant (only the verdict is scoped)")))

         (is (= 201 (:status amber-judgement-post)))

         (testing "an amber Judgement implies a verdict readable by members of the same group only"
           (let [{status-1 :status
                  verdict-1 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type amber-observable)
                           "/" (:value amber-observable)
                           "/verdict")
                      :headers {"Authorization" "foouser"})
                 {status-2 :status
                  verdict-2 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type amber-observable)
                           "/"
                           (:value amber-observable)
                           "/verdict")
                      :headers {"Authorization" "baruser"})
                 {status-3 :status
                  verdict-3 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type amber-observable)
                           "/"
                           (:value amber-observable)
                           "/verdict")
                      :headers {"Authorization" "foobaruser"})]

             (is (= 404 status-1))
             (is (= 200 status-2))
             (is (= (get-in amber-judgement-post [:parsed-body :id])
                    (:judgement_id verdict-2)))

             (is (= 200 status-3))
             (is (= (get-in amber-judgement-post [:parsed-body :id])
                    (:judgement_id verdict-3)))))

         (testing "a red Judgement implies a verdict readable to the owner only"
           (let [{status-1 :status
                  verdict-1 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type red-observable)
                           "/" (:value red-observable)
                           "/verdict")
                      :headers {"Authorization" "foouser"})
                 {status-2 :status
                  verdict-2 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type red-observable)
                           "/"
                           (:value red-observable)
                           "/verdict")
                      :headers {"Authorization" "baruser"})
                 {status-3 :status
                  verdict-3 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type red-observable)
                           "/"
                           (:value red-observable)
                           "/verdict")
                      :headers {"Authorization" "foobaruser"})]

             (is (= 404 status-1))
             (is (= 404 status-2))
             (is (= 200 status-3))
             (is (= (get-in red-judgement-post [:parsed-body :id])
                    (:judgement_id verdict-3)))))

         (testing "a same-org authorized_groups grant contributes to a group member's verdict"
           ;; XFV-20 regression detector for the intra-org sharing path. baruser
           ;; (bargroup) posts a TLP-red judgement carrying an explicit
           ;; authorized_groups grant for its OWN org (bargroup). foobaruser is a
           ;; member of bargroup but NOT the owner, so for foobaruser's verdict:
           ;;   - the owner-org filter admits the doc (same org, stored groups
           ;;     ["bargroup"]);
           ;;   - within the access-control restriction the red clause requires
           ;;     the owner and the amber/public clauses require a non-red TLP, so
           ;;     the ONLY should-clause that can admit this red doc is
           ;;     {:terms {"authorized_groups" groups}} (stores/es/query.clj).
           ;; A cleanup dropping the authorized_users/authorized_groups should
           ;; clauses would silently break intra-org verdict sharing of red/amber
           ;; judgements with no other test failing -- this subtest fails instead.
           ;; (This is a same-org grant; the FOREIGN-grant non-poisoning property
           ;; is covered by `test-observable-verdict-cross-tenant-isolation`.)
           (let [shared-observable {:type "domain" :value "shared-red.com"}
                 shared-judgement-post
                 (POST app
                       "ctia/judgement"
                       :body (assoc base-judgement
                                    :observable shared-observable
                                    :tlp "red"
                                    :authorized_groups ["bargroup"])
                       :headers {"Authorization" "baruser"})
                 _ (is (= 201 (:status shared-judgement-post))
                       "baruser may grant authorized_groups for its OWN org")
                 {status-owner :status
                  verdict-owner :parsed-body}
                 (GET app
                      (str "ctia/" (:type shared-observable) "/"
                           (:value shared-observable) "/verdict")
                      :headers {"Authorization" "baruser"})
                 {status-member :status
                  verdict-member :parsed-body}
                 (GET app
                      (str "ctia/" (:type shared-observable) "/"
                           (:value shared-observable) "/verdict")
                      :headers {"Authorization" "foobaruser"})
                 {status-foreign :status}
                 (GET app
                      (str "ctia/" (:type shared-observable) "/"
                           (:value shared-observable) "/verdict")
                      :headers {"Authorization" "foouser"})]
             ;; the owner sees its own judgement's verdict
             (is (= 200 status-owner))
             (is (= (get-in shared-judgement-post [:parsed-body :id])
                    (:judgement_id verdict-owner)))
             ;; a same-org non-owner member sees it only via the
             ;; authorized_groups should-clause (red TLP blocks every other one)
             (is (= 200 status-member)
                 "a same-org group member gets the verdict via the authorized_groups grant")
             (is (= (get-in shared-judgement-post [:parsed-body :id])
                    (:judgement_id verdict-member)))
             ;; a foreign org (foogroup) is still excluded by the owner-org filter
             (is (= 404 status-foreign)
                 "a foreign org gets no verdict: the owner-org filter excludes it"))))))))

(deftest with-date-range
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [create-judgement #(POST app
                                 "ctia/judgement"
                               :body %
                               :headers {"Authorization" "45c1f5e3f05d0"})
           now->string #(-> (clj-time/now) (time-coerce/to-string))
           before (now->string) ;; timestamp before judgement entities are created
           _judgement-1 (create-judgement {:observable {:value "127.0.0.1"
                                                        :type "ip"}
                                           :disposition 1
                                           :source "test"
                                           :priority 100
                                           :severity "High"
                                           :confidence "Low"
                                           :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"
                                                        :end_time "2016-12-12T00:00:00.000-00:00"}})
           _judgement-2 (create-judgement {:observable {:value "10.0.0.1"
                                                        :type "ip"}
                                           :disposition 1
                                           :source "test"
                                           :priority 90
                                           :severity "High"
                                           :confidence "Low"
                                           :valid_time {:start_time "2017-02-12T00:00:00.000-00:00"}})
           judgement-3 (create-judgement {:observable {:value "10.0.0.1"
                                                       :type "ip"}
                                          :disposition 3
                                          :source "test"
                                          :priority 99
                                          :severity "High"
                                          :confidence "Low"
                                          :valid_time {:start_time "2018-02-12T00:00:00.000-00:00"}})
           midway (now->string) ;; timestamp in between judgement entity creation
           judgement-4 (create-judgement {:observable {:value "10.0.0.1"
                                                       :type "ip"}
                                          :disposition 2
                                          :source "test"
                                          :priority 99
                                          :severity "High"
                                          :confidence "Low"
                                          :valid_time {:start_time "2020-02-12T00:00:00.000-00:00"}})
           after (now->string) ;; timestamp after judgement entities are created
           ]
       ;; `from` & `to` values are in turn used to compare with judgement's `created` field
       (testing "date-range within judgement's created time"
         (let [{status :status
                verdict :parsed-body}
               (GET app
                   "ctia/ip/10.0.0.1/verdict"
                 :headers {"Authorization" "45c1f5e3f05d0"}
                 :query-params {:from before :to midway})]
           (is (= 200 status))
           (is (= {:type "verdict"
                   :disposition 3
                   :disposition_name "Suspicious"
                   :judgement_id (:id (:parsed-body judgement-3))
                   :observable {:value "10.0.0.1", :type "ip"}
                   :valid_time {:start_time #inst "2018-02-12T00:00:00.000-00:00",
                                :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
                  verdict))))
       (testing "date-range outside judgement's created time"
         (let [{status :status
                parsed-body :parsed-body}
               (GET app
                   "ctia/ip/10.0.0.1/verdict"
                 :headers {"Authorization" "45c1f5e3f05d0"}
                 :query-params {:from after})]
           (is (= 404 status))
           (is (= {:message "no verdict currently available for the supplied observable"}
                  parsed-body))))
       ;; tests the implementation where `valid_time` is queried instead of `created` field
       (testing "without date-range parameters"
         (let [{status :status
                verdict :parsed-body}
               (GET app
                   "ctia/ip/10.0.0.1/verdict"
                 :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 200 status))
           (is (= {:type "verdict"
                   :disposition 2
                   :disposition_name "Malicious"
                   :judgement_id (:id (:parsed-body judgement-4))
                   :observable {:value "10.0.0.1", :type "ip"}
                   :valid_time {:start_time #inst "2020-02-12T00:00:00.000-00:00",
                                :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
                  verdict))))))))

(deftest test-observable-verdict-access-control-max-record-visibility
  ;; XFV-20: the verdict path is now unconditionally org-scoped (see
  ;; `ctia.entity.judgement.es-store/verdict-owner-filter`), so a caller in
  ;; another org gets a 404 verdict for foogroup's green judgement regardless of
  ;; `max-record-visibility`. That means the verdict route ALONE can no longer
  ;; detect a revert of the public-TLP clause in
  ;; `ctia.stores.es.query/find-restriction-query-part` (the `everyone` branch).
  ;; To keep this test discriminating, we run it under BOTH settings and
  ;; separate the two concerns:
  ;;   * the *document* read still follows `max-record-visibility`
  ;;     (403 under `group`, 200 under `everyone`); this remains the regression
  ;;     detector for the public-TLP read clause on the document path (also
  ;;     covered by the entity-level access-control tests), and
  ;;   * the *verdict* stays tenant-local (404 for a foreign org under BOTH
  ;;     settings). If the owner filter were reverted, the verdict for baruser
  ;;     would flip to 200 under `everyone` and this test would fail.
  (letfn [(run-visibility-test [visibility]
            (helpers/with-properties (into es-helpers/basic-auth-properties
                                           ["ctia.access-control.max-record-visibility" visibility])
              (fixture-ctia-with-app
               (fn [app]
                 (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
                 (helpers/set-capabilities! app "baruser" ["bargroup"] "user" all-capabilities)
                 (whoami-helpers/set-whoami-response app "foouser" "foouser" "foogroup" "user")
                 (whoami-helpers/set-whoami-response app "baruser" "baruser" "bargroup" "user")
                 (testing (str "verdict route TLP behavior under max-record-visibility=" visibility)
                   (let [green-observable {:type "domain" :value "green.com"}
                         base-judgement {:valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}
                                         :observable green-observable
                                         :reason_uri "https://example.com/"
                                         :source "Example"
                                         :disposition 2
                                         :disposition_name "Malicious"
                                         :reason "Example judgement"
                                         :source_uri "https://example.com/"
                                         :priority 0
                                         :severity "None"
                                         :tlp "green"
                                         :confidence "None"}
                         green-judgement-post
                         (POST app
                               "ctia/judgement?wait_for=true"
                               :body (assoc base-judgement
                                            :observable green-observable
                                            :tlp "green")
                               :headers {"Authorization" "foouser"})
                         green-judgement-id (get-in green-judgement-post [:parsed-body :id])
                         green-judgement-short-id (some-> green-judgement-id id/long-id->id :short-id)]
                     (assert (= 201 (:status green-judgement-post))
                             "the test was not properly initialized")

                     (testing "the owning org always gets its own green judgement's verdict"
                       (let [{status :status verdict :parsed-body}
                             (GET app
                                  (str "ctia/" (:type green-observable) "/" (:value green-observable) "/verdict")
                                  :headers {"Authorization" "foouser"})]
                         (is (= 200 status))
                         (is (= green-judgement-id (:judgement_id verdict)))))

                     (testing "the verdict is tenant-local: another org gets 404 regardless of max-record-visibility"
                       (let [{status :status}
                             (GET app
                                  (str "ctia/" (:type green-observable) "/" (:value green-observable) "/verdict")
                                  :headers {"Authorization" "baruser"})]
                         (is (= 404 status)
                             "a green judgement owned by another org must not contribute to the caller's verdict, even under max-record-visibility=everyone")))

                     (testing "the document read still follows max-record-visibility (the concern the verdict path no longer discriminates)"
                       (let [{status :status}
                             (GET app
                                  (str "ctia/judgement/" green-judgement-short-id)
                                  :headers {"Authorization" "baruser"})]
                         (is (= (if (= "everyone" visibility) 200 403) status)
                             "cross-tenant document read of a green judgement is allowed only under max-record-visibility=everyone; otherwise the access-control read denial is a 403")))))))))]
    (run-visibility-test "group")
    (run-visibility-test "everyone")))

(deftest test-observable-verdict-cross-tenant-isolation
  ;; XFV-20: a verdict is a tenant-local trust decision. A judgement owned by
  ;; another org that merely lists the caller's org in authorized_groups must
  ;; not appear in (poison) the caller's verdict. Such a record can only be
  ;; pre-existing -- the write path now rejects a foreign authorized_groups --
  ;; so we inject the grant directly in the store to simulate pre-fix data.
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "attacker" ["attacker-org"] "user" all-capabilities)
     (helpers/set-capabilities! app "victim" ["victim-org"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app "attacker-key" "attacker" "attacker-org" "user")
     (whoami-helpers/set-whoami-response app "victim-key" "victim" "victim-org" "user")
     (let [judgement-store (helpers/get-store app :judgement)
           params {:refresh "wait_for"}
           attacker-ident {:login "attacker" :groups ["attacker-org"]}]
       (testing "attacker creates a Clean judgement it legitimately owns"
         (let [{status :status
                attacker-judgement :parsed-body}
               (POST app
                     "ctia/judgement"
                     ;; tlp "red" so the ONLY clause that could let this
                     ;; foreign-owned judgement into the victim's verdict is the
                     ;; injected authorized_groups grant (a green/white default
                     ;; would already be visible via the public-TLP clause under
                     ;; max-record-visibility=everyone, masking a partial revert).
                     :body {:observable {:value "203.0.113.213" :type "ip"}
                            :source "poc-cross-tenant"
                            :disposition 1
                            :priority 99
                            :severity "High"
                            :confidence "High"
                            :tlp "red"
                            :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                     :headers {"Authorization" "attacker-key"})
               attacker-short-id (some-> (:id attacker-judgement) id/long-id->id :short-id)]
           (is (= 201 status))

           (testing "simulate pre-fix poisoning: grant the victim org/user via authorized_groups AND authorized_users directly in the store"
             ;; XFV-20: the owner filter is a pure `groups` filter, so it
             ;; neutralizes BOTH a foreign `authorized_groups` grant and a
             ;; foreign `authorized_users` grant identically -- inject both so
             ;; the end-to-end assertion covers the `authorized_users` carve-out
             ;; the guard's docstring claims, not only `authorized_groups`.
             (let [stored (store/read-record judgement-store attacker-short-id attacker-ident params)]
               (store/update-record judgement-store
                                    attacker-short-id
                                    (assoc stored
                                           :authorized_groups ["victim-org"]
                                           :authorized_users ["victim"])
                                    attacker-ident
                                    params))
             ;; sanity: confirm the foreign grants actually persisted, otherwise
             ;; the isolation assertions below could pass vacuously.
             (let [reread (store/read-record judgement-store attacker-short-id attacker-ident params)]
               (is (contains? (set (:authorized_groups reread)) "victim-org")
                   "the injected foreign authorized_groups grant must be stored")
               (is (contains? (set (:authorized_users reread)) "victim")
                   "the injected foreign authorized_users grant must be stored")))

           (testing "the victim's verdict must NOT be poisoned by the foreign-owned judgement"
             (let [{status :status}
                   (GET app
                        "ctia/ip/203.0.113.213/verdict"
                        :headers {"Authorization" "victim-key"})]
               (is (= 404 status)
                   "a judgement owned by another org must not contribute to the caller's verdict")))

           (testing "the owning (attacker) org still gets its own judgement's verdict"
             (let [{status :status
                    verdict :parsed-body}
                   (GET app
                        "ctia/ip/203.0.113.213/verdict"
                        :headers {"Authorization" "attacker-key"})]
               (is (= 200 status))
               (is (= (:id attacker-judgement) (:judgement_id verdict)))))))

       (testing "the victim's own judgement still produces a verdict (guard is not over-broad)"
         (let [{status :status
                victim-judgement :parsed-body}
               (POST app
                     "ctia/judgement"
                     :body {:observable {:value "198.51.100.7" :type "ip"}
                            :source "victim-local"
                            :disposition 2
                            :priority 99
                            :severity "High"
                            :confidence "High"
                            :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                     :headers {"Authorization" "victim-key"})]
           (is (= 201 status))
           (let [{status :status
                  verdict :parsed-body}
                 (GET app
                      "ctia/ip/198.51.100.7/verdict"
                      :headers {"Authorization" "victim-key"})]
             (is (= 200 status))
             (is (= (:id victim-judgement) (:judgement_id verdict))))))

       (testing "a higher-priority foreign judgement cannot outrank the victim's own verdict"
         ;; The attacker owns a high-priority (99) Clean judgement and grants it
         ;; to the victim via authorized_groups; the victim has its own
         ;; lower-priority (50) Malicious judgement on the same observable.
         ;; Pre-fix the attacker's priority-99 Clean would dominate the verdict;
         ;; post-fix the foreign judgement is excluded entirely, so the victim's
         ;; own Malicious verdict stands.
         (let [{astatus :status
                poison :parsed-body}
               (POST app
                     "ctia/judgement"
                     ;; tlp "red" (see above): the injected authorized_groups
                     ;; grant is the only path by which this priority-99 Clean
                     ;; could reach the victim's verdict pre-fix.
                     :body {:observable {:value "203.0.113.220" :type "ip"}
                            :source "poc-priority"
                            :disposition 1
                            :priority 99
                            :severity "High"
                            :confidence "High"
                            :tlp "red"
                            :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                     :headers {"Authorization" "attacker-key"})
               poison-short-id (some-> (:id poison) id/long-id->id :short-id)
               {vstatus :status
                victim-judgement :parsed-body}
               (POST app
                     "ctia/judgement"
                     :body {:observable {:value "203.0.113.220" :type "ip"}
                            :source "victim-local"
                            :disposition 2
                            :priority 50
                            :severity "High"
                            :confidence "High"
                            :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                     :headers {"Authorization" "victim-key"})]
           (is (= 201 astatus))
           (is (= 201 vstatus))
           (let [stored (store/read-record judgement-store poison-short-id attacker-ident params)]
             (store/update-record judgement-store
                                  poison-short-id
                                  (assoc stored :authorized_groups ["victim-org"])
                                  attacker-ident
                                  params))
           ;; sanity (mirrors the first sub-test): confirm the foreign grant
           ;; actually persisted, otherwise this isolation assertion could pass
           ;; vacuously if `update-record` ever stopped persisting the field.
           (is (contains? (set (:authorized_groups
                                (store/read-record judgement-store poison-short-id attacker-ident params)))
                          "victim-org")
               "the injected foreign authorized_groups grant must be stored")
           (let [{status :status
                  verdict :parsed-body}
                 (GET app
                      "ctia/ip/203.0.113.220/verdict"
                      :headers {"Authorization" "victim-key"})]
             (is (= 200 status))
             (is (= (:id victim-judgement) (:judgement_id verdict))
                 "the victim's own Malicious judgement must win, not the attacker's higher-priority Clean")
             (is (= 2 (:disposition verdict))))))))))
