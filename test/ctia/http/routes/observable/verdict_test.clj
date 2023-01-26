(ns ctia.http.routes.observable.verdict-test
  (:require [clj-momo.lib.time :as time]
            [clj-momo.test-helpers.core :as mht]
            [clj-time.core :as clj-time]
            [clj-time.coerce :as time-coerce]
            [clj-time.format :as time-format]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
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
             auth-observable
             {:type "domain"
              :value "auth.com"}
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
                   :headers {"Authorization" "foobaruser"})
             authorized-groups-judgement-post
             (POST app
                   "ctia/judgement"
                   :body (assoc base-judgement
                                :observable auth-observable
                                :tlp "red"
                                :authorized_groups ["bargroup"])
                   :headers {"Authorization" "baruser"})]

         (is (= 201 (:status green-judgement-post)))

         (testing "a green Judgement implies a verdict readable by everyone"
           (let [{status-1 :status
                  verdict-1 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type green-observable)
                           "/" (:value green-observable)
                           "/verdict")
                      :headers {"Authorization" "foouser"})
                 {status-2 :status
                  verdict-2 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type green-observable)
                           "/"
                           (:value green-observable)
                           "/verdict")
                      :headers {"Authorization" "baruser"})
                 {status-3 :status
                  verdict-3 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type green-observable)
                           "/"
                           (:value green-observable)
                           "/verdict")
                      :headers {"Authorization" "foobaruser"})]

             (is (= 200 status-1))
             (is (= (get-in green-judgement-post [:parsed-body :id])
                    (:judgement_id verdict-1)))

             (is (= 200 status-2))
             (is (= (get-in green-judgement-post [:parsed-body :id])
                    (:judgement_id verdict-2)))

             (is (= 200 status-3))
             (is (= (get-in green-judgement-post [:parsed-body :id])
                    (:judgement_id verdict-3)))))

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

         (testing "a Judgement with authorized_groups"
           (let [{status-1 :status
                  verdict-1 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type auth-observable)
                           "/" (:value auth-observable)
                           "/verdict")
                      :headers {"Authorization" "foouser"})
                 {status-2 :status
                  verdict-2 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type auth-observable)
                           "/"
                           (:value auth-observable)
                           "/verdict")
                      :headers {"Authorization" "baruser"})
                 {status-3 :status
                  verdict-3 :parsed-body}
                 (GET app
                      (str "ctia/"
                           (:type auth-observable)
                           "/"
                           (:value auth-observable)
                           "/verdict")
                      :headers {"Authorization" "foobaruser"})]

             (is (= 404 status-1))
             (is (= 200 status-2))
             (is (= 200 status-3))
             (is (= (get-in authorized-groups-judgement-post [:parsed-body :id])
                    (:judgement_id verdict-3))))))))))

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
  (helpers/with-properties (into es-helpers/basic-auth-properties
                                 ["ctia.access-control.max-record-visibility" "group"])
  (fixture-ctia-with-app
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
               auth-observable
               {:type "domain"
                :value "auth.com"}
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
                     "ctia/judgement?wait_for=true"
                     :body (assoc base-judgement
                                  :observable green-observable
                                  :tlp "green")
                     :headers {"Authorization" "foouser"})]
           (assert (= 201 (:status green-judgement-post))
                   "the test was not properly initialized")

           (testing "a green Judgement should only affect verdicts of the group when visibility is set to group."
             (let [{status-1 :status
                    verdict-1 :parsed-body}
                   (GET app
                        (str "ctia/"
                             (:type green-observable)
                             "/" (:value green-observable)
                             "/verdict")
                        :headers {"Authorization" "foouser"})
                   {status-2 :status
                    verdict-2 :parsed-body}
                   (GET app
                        (str "ctia/"
                             (:type green-observable)
                             "/"
                             (:value green-observable)
                             "/verdict")
                        :headers {"Authorization" "baruser"})]
               (is (= 200 status-1))
               (is (= (get-in green-judgement-post [:parsed-body :id])
                      (:judgement_id verdict-1)))
               (is (= 404 status-2))))))))))
