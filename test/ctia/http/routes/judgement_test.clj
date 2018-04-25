(ns ctia.http.routes.judgement-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.lib.clj-time.core :as time]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            ctia.properties
            [ctia.entity.judgement.schemas :refer [judgement-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [get post post-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store]]]
            [ctim.domain.id :as id]
            [ctim.examples.judgements :as ex :refer [new-judgement-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(def new-judgement
  (merge new-judgement-maximal
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

(defn additional-tests [judgement-id judgement]
  (testing "GET /ctia/judgement/search"
    ;; only when ES store
    (when (= "es" (get-in @ctia.properties/properties [:ctia :store :indicator]))
      (let [term "observable.value:\"1.2.3.4\""
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "IP quoted term works"))

      (let [term "1.2.3.4"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "IP unquoted, all term works"))

      (let [term "Evil Servers"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "Full text search, mixed case, _all term works"))

      (let [term "disposition_name:Malicious"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "uppercase term works"))

      (let [term "disposition_name:malicious"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "lowercase quoted term works"))

      (let [term "disposition_name:Malicious"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term
                                         "tlp" "red"})]
        (is (= 200 (:status response)))
        (is (empty? (:parsed-body response))
            "filters are applied, and discriminate"))

      (let [term "disposition_name:Malicious"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term
                                         "tlp" "green"})]
        (is (= 200 (:status response)))
        (is (= 1  (count (:parsed-body response)))
            "filters are applied, and match properly"))))

  (testing "GET /ctia/judgement/:id authentication failures"
    (testing "no Authorization"
      (let [{body :parsed-body status :status}
            (get (str "ctia/judgement/" (:short-id judgement-id)))]
        (is (= 403 status))
        (is (= {:message "Only authenticated users allowed"} body))))

    (testing "unknown Authorization"
      (let [{body :parsed-body status :status}
            (get (str "ctia/judgement/" (:short-id judgement-id))
                 :headers {"Authorization" "1111111111111"})]
        (is (= 403 status))
        (is (= {:message "Only authenticated users allowed"} body))))

    (testing "doesn't have read capability"
      (let [{body :parsed-body status :status}
            (get (str "ctia/judgement/" (:short-id judgement-id))
                 :headers {"Authorization" "2222222222222"})]
        (is (= 401 status))
        (is (= {:message "Missing capability",
                :capabilities :read-judgement,
                :owner "baruser"}
               body))))

    (testing "doesn't have list by external id capability"
      (let [{body :parsed-body status :status}
            (get  "ctia/judgement/external_id/123"
                  :headers {"Authorization" "2222222222222"})]
        (is (= 401 status))
        (is (= {:message "Missing capability",
                :capabilities #{:read-judgement :external-id},
                :owner "baruser"}
               body))))))

(deftest test-judgement-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroupi"] "user" all-capabilities)
     (helpers/set-capabilities! "baruser"  ["bargroup"] "user" #{})
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (whoami-helpers/set-whoami-response "2222222222222"
                                         "baruser"
                                         "bargroup"
                                         "user")

     (entity-crud-test
      {:entity "judgement"
       :example new-judgement
       :update-tests? false
       :invalid-tests? false
       :search-tests? false
       :additional-tests additional-tests
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-judgement-with-jwt-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (helpers/set-capabilities! "baruser" ["bargroup"] "user" #{})
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "foogroup" "user")
     (whoami-helpers/set-whoami-response "2222222222222" "baruser" "bargroup" "user")

     (testing "POST /ctia/judgement"
       (let [{judgement :parsed-body
              status :status}
             (post "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :external_ids ["http://ex.tld/ctia/judgement/judgement-123"
                                         "http://ex.tld/ctia/judgement/judgement-456"]
                          :disposition 2
                          :source "test"
                          :priority 100
                          :severity "High"
                          :confidence "Low"
                          :reason "This is a bad IP address that talked to some evil servers"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})
             judgement-id (id/long-id->id (:id judgement))
             judgement-external-ids (:external_ids judgement)]

         (is (= 201 status))

         (testing "GET /ctia/judgement/:id with bad JWT Authorization header"
           (let [{status :status
                  judgement :parsed-body
                  :as response}
                 (get (str "ctia/judgement/" (:short-id judgement-id))
                      :headers {"Authorization" "Bearer 45c1f5e3f05d0"})]
             (is (= 401 (:status response)))))

         (testing "GET /ctia/judgement/:id with JWT Authorization header"
           (with-redefs [time/now (constantly (time/date-time 2017 02 16 0 0 0))]
             (let [jwt-token "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL3VzZXJcL2VtYWlsIjoiZ2J1aXNzb24rcWFfc2RjX2lyb2hAY2lzY28uY29tIiwiaHR0cHM6XC9cL3NjaGVtYXMuY2lzY28uY29tXC9pcm9oXC9pZGVudGl0eVwvY2xhaW1zXC91c2VyXC9pZHBcL2lkIjoiYW1wIiwiaHR0cHM6XC9cL3NjaGVtYXMuY2lzY28uY29tXC9pcm9oXC9pZGVudGl0eVwvY2xhaW1zXC91c2VyXC9uaWNrIjoiZ2J1aXNzb24rcWFfc2RjX2lyb2hAY2lzY28uY29tIiwiZW1haWwiOiJnYnVpc3NvbitxYV9zZGNfaXJvaEBjaXNjby5jb20iLCJzdWIiOiI1NmJiNWY4Yy1jYzRlLTRlZDMtYTkxYS1jNjYwNDI4N2ZlMzIiLCJpc3MiOiJJUk9IIEF1dGgiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL3Njb3BlcyI6WyJjYXNlYm9vayIsImdsb2JhbC1pbnRlbCIsInByaXZhdGUtaW50ZWwiLCJjb2xsZWN0IiwiZW5yaWNoIiwiaW5zcGVjdCIsImludGVncmF0aW9uIiwiaXJvaC1hdXRoIiwicmVzcG9uc2UiLCJ1aS1zZXR0aW5ncyJdLCJleHAiOjE0ODc3NzI4NTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvb2F1dGhcL2NsaWVudFwvbmFtZSI6Imlyb2gtdWkiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29yZ1wvaWQiOiI2MzQ4OWNmOS01NjFjLTQ5NTgtYTEzZC02ZDg0YjdlZjA5ZDQiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29yZ1wvbmFtZSI6IklST0ggVGVzdGluZyIsImp0aSI6ImEyNjhhZTdhMy0wOWM5LTQxNDktYjQ5NS1iOThjOGM1ZGU2NjYiLCJuYmYiOjE0ODcxNjc3NTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvdXNlclwvaWQiOiI1NmJiNWY4Yy1jYzRlLTRlZDMtYTkxYS1jNjYwNDI4N2ZlMzIiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29hdXRoXC9jbGllbnRcL2lkIjoiaXJvaC11aSIsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvdmVyc2lvbiI6IjEiLCJpYXQiOjE0ODcxNjgwNTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvb2F1dGhcL2tpbmQiOiJzZXNzaW9uLXRva2VuIn0.jl0r3LiL6qOy6DIDZs5NRiQBHlJEzXFXUvKXGPd2PL66xSE0v0Bkc6FD3vPccYxvk-tWBMJX8oiDuAgYt2eRU05blPtzy1yQ-V-zJtxnpuQbDzvVytZvE9n1_8NdvcLa9eXBjUkJ2FsXAIguXpVDIbR3zs9MkjfyrsKeVCmhC3QTehj55Rf-WINeTq0UflIyoZqfK5Mewl-DBwbvTRjTIRJpNPhjErJ0ypHNXzTKM-nVljSRhrfpoBYpPxQSQVTedWIA2Sks4fBvEwdeE60aBRK1HeTps0G1h3RXPYu7q1I5ti9a2axiQtRLA11CxoOvMmnjyWkffi5vyrFKqZ7muQ"
                   {status :status
                    judgement :parsed-body
                    :as response}
                   (get (str "ctia/judgement/" (:short-id judgement-id))
                        :headers {"Authorization" (str "Bearer " jwt-token)})]
               (is (= 200 (:status response)))
               (is (= {:id (id/long-id judgement-id)
                       :type "judgement"
                       :observable {:value "1.2.3.4"
                                    :type "ip"}
                       :external_ids ["http://ex.tld/ctia/judgement/judgement-123"
                                      "http://ex.tld/ctia/judgement/judgement-456"]
                       :disposition 2
                       :disposition_name "Malicious"
                       :priority 100
                       :severity "High"
                       :confidence "Low"
                       :source "test"
                       :tlp "green"
                       :schema_version schema-version
                       :reason "This is a bad IP address that talked to some evil servers"
                       :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                    :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
                      judgement))))))))))

(deftest test-judgement-routes-for-dispositon-determination
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "POST a judgement with dispositon (id)"
       (let [{status :status
              judgement :parsed-body}
             (post "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :disposition 2
                          :source "test"
                          :priority 100
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))
         (is (deep=
              {:type "judgement"
               :observable {:value "1.2.3.4"
                            :type "ip"}
               :disposition 2
               :disposition_name "Malicious"
               :source "test"
               :priority 100
               :severity "High"
               :confidence "Low"
               :tlp "green"
               :schema_version schema-version
               :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                            :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
              (dissoc judgement
                      :id)))))

     (testing "POST a judgement with disposition_name"
       (let [{status :status
              judgement :parsed-body}
             (post "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :disposition_name "Malicious"
                          :source "test"
                          :priority 100
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))
         (is (deep=
              {:type "judgement"
               :observable {:value "1.2.3.4"
                            :type "ip"}
               :disposition 2
               :disposition_name "Malicious"
               :source "test"
               :priority 100
               :severity "High"
               :confidence "Low"
               :tlp "green"
               :schema_version schema-version
               :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                            :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
              (dissoc judgement
                      :id)))))

     (testing "POST a judgement without disposition"
       (let [{status :status
              judgement :parsed-body}
             (post "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :source "test"
                          :priority 100
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))
         (is (deep=
              {:type "judgement"
               :observable {:value "1.2.3.4"
                            :type "ip"}
               :disposition 5
               :disposition_name "Unknown"
               :source "test"
               :priority 100
               :severity "High"
               :confidence "Low"
               :tlp "green"
               :schema_version schema-version
               :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                            :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
              (dissoc judgement
                      :id)))))

     (testing "POST a judgement with mismatching disposition/disposition_name"
       (let [{status :status
              judgement :parsed-body}
             (post "ctia/judgement"
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
              {:error "Mismatching :dispostion and dispositon_name for judgement",
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

     (testing "POST a judgement with mismatching disposition/disposition_name"
       (let [{status :status
              judgement :parsed-body}
             (post "ctia/judgement"
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
         (is (deep=
              {:error "Mismatching :dispostion and dispositon_name for judgement",
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

(deftest test-judgement-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [new-judgement
           (assoc new-judgement-maximal
                  :observable
                  {:value "1.2.3.4", :type "ip"})
           ids (post-entity-bulk
                new-judgement
                :judgements
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        "ctia/ip/1.2.3.4/judgements"
        {"Authorization" "45c1f5e3f05d0"}
        [:id :disposition :priority :severity :confidence])

       (field-selection-tests
        ["ctia/ip/1.2.3.4/judgements"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        judgement-fields)))))

(deftest test-judgement-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "judgement"
                          ex/new-judgement-minimal
                          false
                          true))))
