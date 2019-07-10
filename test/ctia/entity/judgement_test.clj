(ns ctia.entity.judgement-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.lib.clj-time.core :as time]
            [clj-momo.test-helpers.core :as mth]
            [clj-time.core :as t]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            ctia.properties
            [ctia.entity.judgement.schemas
             :refer [judgement-fields judgement-sort-fields]]
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
            [clj-jwt.core :as jwt]
            [clj-jwt.key :as jwt-key]
            [ctim.domain.id :as id]
            [ctia.test-helpers.es :as es-helpers]
            [ctim.examples.judgements :as ex :refer [new-judgement-maximal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    helpers/fixture-properties:cors
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
        (is (= 401 status))
        (is (= {:message "Only authenticated users allowed"
                :error :not_authenticated}
               body))))

    (testing "unknown Authorization"
      (let [{body :parsed-body status :status}
            (get (str "ctia/judgement/" (:short-id judgement-id))
                 :headers {"Authorization" "1111111111111"})]
        (is (= 401 status))
        (is (= {:message "Only authenticated users allowed"
                :error :not_authenticated}
               body))))

    (testing "doesn't have read capability"
      (let [{body :parsed-body status :status}
            (get (str "ctia/judgement/" (:short-id judgement-id))
                 :headers {"Authorization" "2222222222222"})]
        (is (= 403 status))
        (is (= {:message "Missing capability",
                :capabilities :read-judgement,
                :owner "baruser"
                :error :missing_capability}
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
       :update-tests? true
       :update-field :source
       :invalid-tests? false
       :search-tests? false
       :additional-tests additional-tests
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(defn apply-fixtures
  [properties fn]
  (let [fixture-fn
        (join-fixtures [helpers/fixture-properties:clean
                        helpers/fixture-properties:cors
                        #(helpers/with-properties properties (%))
                        es-helpers/fixture-properties:es-store
                        helpers/fixture-ctia
                        es-helpers/fixture-delete-store-indexes])]
    (fixture-fn fn)))

(def new-judgement-1
  {:observable {:value "1.2.3.4"
                :type "ip"}
   :external_ids ["http://ex.tld/ctia/judgement/judgement-123"
                  "http://ex.tld/ctia/judgement/judgement-456"]
   :disposition 2
   :source "test"
   :priority 100
   :timestamp #inst "2042-01-01T00:00:00.000Z"
   :severity "High"
   :confidence "Low"
   :reason "This is a bad IP address that talked to some evil servers"
   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

(deftest jwt-related-tests-on-judgements
  (apply-fixtures
   ["ctia.http.jwt.enabled" true
    "ctia.http.jwt.public-key-map"
    "IROH Auth=resources/cert/ctia-jwt.pub,IROH Auth TEST=resources/cert/ctia-jwt-2.pub"]
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (helpers/set-capabilities! "baruser" ["bargroup"] "user" #{})
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "foogroup" "user")
     (whoami-helpers/set-whoami-response "2222222222222" "baruser" "bargroup" "user")

     (let [clm (fn [k] (str "https://schemas.cisco.com/iroh/identity/claims/" k))
           priv-key-1 (jwt-key/private-key "resources/cert/ctia-jwt.key")
           priv-key-2 (jwt-key/private-key "resources/cert/ctia-jwt-2.key")
           now (t/now)
           in-one-hour (t/plus now (t/hours 1))
           claims-1
           {:exp in-one-hour
            :iat now
            :iss "IROH Auth",
            :email "gbuisson+qa_sdc_iroh@cisco.com",
            :nbf now
            "sub" "56bb5f8c-cc4e-4ed3-a91a-c6604287fe32"
            :jti "a268ae7a3-09c9-4149-b495-b98c8c5de666",
            (clm "oauth/client/id") "iroh-ui",
            (clm "oauth/client/name") "iroh-ui",
            (clm "oauth/kind") "session-token",
            (clm "org/id") "63489cf9-561c-4958-a13d-6d84b7ef09d4",
            (clm "org/name") "IROH Testing",
            (clm "scopes") ["casebook","global-intel","private-intel","collect",
                            "enrich","inspect","integration","iroh-auth",
                            "response","ui-settings"],
            (clm "user/email") "gbuisson+qa_sdc_iroh@cisco.com",
            (clm "user/id") "56bb5f8c-cc4e-4ed3-a91a-c6604287fe32",
            (clm "user/idp/id") "amp",
            (clm "user/nick") "gbuisson+qa_sdc_iroh@cisco.com",
            (clm "version") "1"}
           claims-2 (assoc claims-1 :iss "IROH Auth TEST")
           jwt-1 (-> claims-1 jwt/jwt (jwt/sign :RS256 priv-key-1) jwt/to-str)
           bad-iss-jwt-1 (-> claims-1 (assoc :iss "IROH Auth TEST") jwt/jwt (jwt/sign :RS256 priv-key-1) jwt/to-str)
           jwt-2 (-> claims-2 jwt/jwt (jwt/sign :RS256 priv-key-2) jwt/to-str)
           bad-iss-jwt-2 (-> claims-2 (assoc :iss "IROH Auth") jwt/jwt (jwt/sign :RS256 priv-key-2) jwt/to-str)]
       (testing "POST /ctia/judgement"
         (let [{judgement :parsed-body
                status :status
                :as resp}
               (post "ctia/judgement"
                     :body new-judgement-1
                     :headers {"Authorization" "45c1f5e3f05d0"
                               "origin" "http://external.cisco.com"})
               judgement-id (id/long-id->id (:id judgement))
               judgement-external-ids (:external_ids judgement)

               get-judgement (fn [jwt]
                               (let [{:keys [status] :as response}
                                     (get (str "ctia/judgement/" (:short-id judgement-id))
                                          :headers {"Authorization" (str "Bearer " jwt)})]
                                 (:status response)))]
           (is (= 201 status))
           (testing "GET /ctia/judgement/:id with bad JWT Authorization header"
             (let [{:keys [status] :as response}
                   (get (str "ctia/judgement/" (:short-id judgement-id))
                        :headers {"Authorization" "Bearer 45c1f5e3f05d0"})]
               (is (= 401 (:status response)))))

           (testing "GET /ctia/judgement/:id"
             (testing "Mulitple JWT keys"
               (testing "Key 1 with correct issuer"
                 (is (= 200 (get-judgement jwt-1))))
               (testing "Key 1 with wrong issuer"
                 (is (= 401 (get-judgement bad-iss-jwt-1))))
               (testing "Key 2 with correct issuer"
                 (is (= 200 (get-judgement jwt-2))))
               (testing "Key 2 with wrong issuer"
                 (is (= 401 (get-judgement bad-iss-jwt-2))))))))))))

  (deftest test-judgement-with-jwt-routes
    (test-for-each-store
     (fn []
       (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
       (helpers/set-capabilities! "baruser" ["bargroup"] "user" #{})
       (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "foogroup" "user")
       (whoami-helpers/set-whoami-response "2222222222222" "baruser" "bargroup" "user")

       (testing "POST /ctia/judgement"
         (let [{judgement :parsed-body
                status :status
                :as resp}
               (post "ctia/judgement"
                     :body {:observable {:value "1.2.3.4"
                                         :type "ip"}
                            :external_ids ["http://ex.tld/ctia/judgement/judgement-123"
                                           "http://ex.tld/ctia/judgement/judgement-456"]
                            :disposition 2
                            :source "test"
                            :priority 100
                            :timestamp #inst "2042-01-01T00:00:00.000Z"
                            :severity "High"
                            :confidence "Low"
                            :reason "This is a bad IP address that talked to some evil servers"
                            :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                     :headers {"Authorization" "45c1f5e3f05d0"
                               "origin" "http://external.cisco.com"})
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
                         :timestamp #inst "2042-01-01T00:00:00.000Z"
                         :severity "High"
                         :confidence "Low"
                         :source "test"
                         :tlp "green"
                         :schema_version schema-version
                         :reason "This is a bad IP address that talked to some evil servers"
                         :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                      :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
                        judgement)))))

           (testing "POST /ctia/judgement with JWT Authorization header"
             (with-redefs [time/now (constantly (time/date-time 2017 02 16 0 0 0))]
               (let [jwt-token "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL3VzZXJcL2VtYWlsIjoiZ2J1aXNzb24rcWFfc2RjX2lyb2hAY2lzY28uY29tIiwiaHR0cHM6XC9cL3NjaGVtYXMuY2lzY28uY29tXC9pcm9oXC9pZGVudGl0eVwvY2xhaW1zXC91c2VyXC9pZHBcL2lkIjoiYW1wIiwiaHR0cHM6XC9cL3NjaGVtYXMuY2lzY28uY29tXC9pcm9oXC9pZGVudGl0eVwvY2xhaW1zXC91c2VyXC9uaWNrIjoiZ2J1aXNzb24rcWFfc2RjX2lyb2hAY2lzY28uY29tIiwiZW1haWwiOiJnYnVpc3NvbitxYV9zZGNfaXJvaEBjaXNjby5jb20iLCJzdWIiOiI1NmJiNWY4Yy1jYzRlLTRlZDMtYTkxYS1jNjYwNDI4N2ZlMzIiLCJpc3MiOiJJUk9IIEF1dGgiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL3Njb3BlcyI6WyJjYXNlYm9vayIsImdsb2JhbC1pbnRlbCIsInByaXZhdGUtaW50ZWwiLCJjb2xsZWN0IiwiZW5yaWNoIiwiaW5zcGVjdCIsImludGVncmF0aW9uIiwiaXJvaC1hdXRoIiwicmVzcG9uc2UiLCJ1aS1zZXR0aW5ncyJdLCJleHAiOjE0ODc3NzI4NTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvb2F1dGhcL2NsaWVudFwvbmFtZSI6Imlyb2gtdWkiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29yZ1wvaWQiOiI2MzQ4OWNmOS01NjFjLTQ5NTgtYTEzZC02ZDg0YjdlZjA5ZDQiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29yZ1wvbmFtZSI6IklST0ggVGVzdGluZyIsImp0aSI6ImEyNjhhZTdhMy0wOWM5LTQxNDktYjQ5NS1iOThjOGM1ZGU2NjYiLCJuYmYiOjE0ODcxNjc3NTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvdXNlclwvaWQiOiI1NmJiNWY4Yy1jYzRlLTRlZDMtYTkxYS1jNjYwNDI4N2ZlMzIiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29hdXRoXC9jbGllbnRcL2lkIjoiaXJvaC11aSIsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvdmVyc2lvbiI6IjEiLCJpYXQiOjE0ODcxNjgwNTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvb2F1dGhcL2tpbmQiOiJzZXNzaW9uLXRva2VuIn0.jl0r3LiL6qOy6DIDZs5NRiQBHlJEzXFXUvKXGPd2PL66xSE0v0Bkc6FD3vPccYxvk-tWBMJX8oiDuAgYt2eRU05blPtzy1yQ-V-zJtxnpuQbDzvVytZvE9n1_8NdvcLa9eXBjUkJ2FsXAIguXpVDIbR3zs9MkjfyrsKeVCmhC3QTehj55Rf-WINeTq0UflIyoZqfK5Mewl-DBwbvTRjTIRJpNPhjErJ0ypHNXzTKM-nVljSRhrfpoBYpPxQSQVTedWIA2Sks4fBvEwdeE60aBRK1HeTps0G1h3RXPYu7q1I5ti9a2axiQtRLA11CxoOvMmnjyWkffi5vyrFKqZ7muQ"
                     {status :status
                      judgement :parsed-body
                      :as response}
                     (post "ctia/judgement"
                           :body {:observable {:value "1.2.3.4"
                                               :type "ip"}
                                  :external_ids ["http://ex.tld/ctia/judgement/judgement-123"
                                                 "http://ex.tld/ctia/judgement/judgement-456"]
                                  :id "http://localhost:3001/ctia/judgement/judgement-00001111-0000-1111-2222-000011112222"
                                  :disposition 2
                                  :source "test"
                                  :priority 100
                                  :severity "High"
                                  :confidence "Low"
                                  :reason "This is a bad IP address that talked to some evil servers"
                                  :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                           :headers {"Authorization" (str "Bearer " jwt-token)
                                     "origin" "http://external.cisco.com"})]
                 (is (= 403 (:status response))
                     "Normal users shouldn't be allowed to set the ids during creation.")))))))))

(def expected-headers
  {"Access-Control-Expose-Headers"
   (str "X-Total-Hits,X-Next,X-Previous,X-Sort,Etag,"
        "X-Ctia-Version,X-Ctia-Config,X-Ctim-Version,"
        "X-RateLimit-GROUP-Limit"),
   "Access-Control-Allow-Origin" "http://external.cisco.com",
   "Access-Control-Allow-Methods" "DELETE, GET, PATCH, POST, PUT"})

(deftest cors-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (helpers/set-capabilities! "baruser" ["bargroup"] "user" #{})
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "foogroup" "user")
     (whoami-helpers/set-whoami-response "2222222222222" "baruser" "bargroup" "user")
     (testing "POST /ctia/judgement"
       (let [{judgement :parsed-body
              status :status
              :as resp}
             (post "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :external_ids ["http://ex.tld/ctia/judgement/judgement-123"
                                         "http://ex.tld/ctia/judgement/judgement-456"]
                          :disposition 2
                          :source "test"
                          :priority 100
                          :timestamp #inst "2042-01-01T00:00:00.000Z"
                          :severity "High"
                          :confidence "Low"
                          :reason "This is a bad IP address that talked to some evil servers"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"
                             "Origin" "http://external.cisco.com"})
             bad-origin-resp (post "ctia/judgement"
                                   :body {:observable {:value "1.2.3.4"
                                                       :type "ip"}
                                          :external_ids ["http://ex.tld/ctia/judgement/judgement-123"
                                                         "http://ex.tld/ctia/judgement/judgement-456"]
                                          :disposition 2
                                          :source "test"
                                          :priority 100
                                          :timestamp #inst "2042-01-01T00:00:00.000Z"
                                          :severity "High"
                                          :confidence "Low"
                                          :reason "This is a bad IP address that talked to some evil servers"
                                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                                   :headers {"Authorization" "45c1f5e3f05d0"
                                             "Origin" "http://badcors.com"})
             judgement-id (id/long-id->id (:id judgement))
             judgement-external-ids (:external_ids judgement)]

         (is (= 201 status))
         (is (= expected-headers
                (select-keys (:headers resp)
                             ["Access-Control-Expose-Headers"
                              "Access-Control-Allow-Origin"
                              "Access-Control-Allow-Methods"]))
             "We should returns the CORS headers when correct origin")
         (is (= 201 (:status bad-origin-resp)))
         (is (= {}
                (select-keys (:headers bad-origin-resp)
                             ["Access-Control-Expose-Headers"
                              "Access-Control-Allow-Origin"
                              "Access-Control-Allow-Methods"]))
             "We shouldn't returns the CORS headers for bad origins")

         (testing "GET /ctia/judgement/:id with bad JWT Authorization header"
           (let [{status :status
                  judgement :parsed-body
                  :as response}
                 (get (str "ctia/judgement/" (:short-id judgement-id))
                      :headers {"Authorization" "Bearer 45c1f5e3f05d0"
                                "Origin" "http://external.cisco.com"})]
             (is (= 401 (:status response)))
             (is (= expected-headers
                    (select-keys (:headers response)
                                 ["Access-Control-Expose-Headers"
                                  "Access-Control-Allow-Origin"
                                  "Access-Control-Allow-Methods"]))
                 "Even if the JWT is refused we should returns the CORS headers")))

         (testing "GET /ctia/judgement/:id with JWT Authorization header"
           (with-redefs [time/now (constantly (time/date-time 2017 02 16 0 0 0))]
             (let [jwt-token "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL3VzZXJcL2VtYWlsIjoiZ2J1aXNzb24rcWFfc2RjX2lyb2hAY2lzY28uY29tIiwiaHR0cHM6XC9cL3NjaGVtYXMuY2lzY28uY29tXC9pcm9oXC9pZGVudGl0eVwvY2xhaW1zXC91c2VyXC9pZHBcL2lkIjoiYW1wIiwiaHR0cHM6XC9cL3NjaGVtYXMuY2lzY28uY29tXC9pcm9oXC9pZGVudGl0eVwvY2xhaW1zXC91c2VyXC9uaWNrIjoiZ2J1aXNzb24rcWFfc2RjX2lyb2hAY2lzY28uY29tIiwiZW1haWwiOiJnYnVpc3NvbitxYV9zZGNfaXJvaEBjaXNjby5jb20iLCJzdWIiOiI1NmJiNWY4Yy1jYzRlLTRlZDMtYTkxYS1jNjYwNDI4N2ZlMzIiLCJpc3MiOiJJUk9IIEF1dGgiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL3Njb3BlcyI6WyJjYXNlYm9vayIsImdsb2JhbC1pbnRlbCIsInByaXZhdGUtaW50ZWwiLCJjb2xsZWN0IiwiZW5yaWNoIiwiaW5zcGVjdCIsImludGVncmF0aW9uIiwiaXJvaC1hdXRoIiwicmVzcG9uc2UiLCJ1aS1zZXR0aW5ncyJdLCJleHAiOjE0ODc3NzI4NTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvb2F1dGhcL2NsaWVudFwvbmFtZSI6Imlyb2gtdWkiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29yZ1wvaWQiOiI2MzQ4OWNmOS01NjFjLTQ5NTgtYTEzZC02ZDg0YjdlZjA5ZDQiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29yZ1wvbmFtZSI6IklST0ggVGVzdGluZyIsImp0aSI6ImEyNjhhZTdhMy0wOWM5LTQxNDktYjQ5NS1iOThjOGM1ZGU2NjYiLCJuYmYiOjE0ODcxNjc3NTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvdXNlclwvaWQiOiI1NmJiNWY4Yy1jYzRlLTRlZDMtYTkxYS1jNjYwNDI4N2ZlMzIiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29hdXRoXC9jbGllbnRcL2lkIjoiaXJvaC11aSIsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvdmVyc2lvbiI6IjEiLCJpYXQiOjE0ODcxNjgwNTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvb2F1dGhcL2tpbmQiOiJzZXNzaW9uLXRva2VuIn0.jl0r3LiL6qOy6DIDZs5NRiQBHlJEzXFXUvKXGPd2PL66xSE0v0Bkc6FD3vPccYxvk-tWBMJX8oiDuAgYt2eRU05blPtzy1yQ-V-zJtxnpuQbDzvVytZvE9n1_8NdvcLa9eXBjUkJ2FsXAIguXpVDIbR3zs9MkjfyrsKeVCmhC3QTehj55Rf-WINeTq0UflIyoZqfK5Mewl-DBwbvTRjTIRJpNPhjErJ0ypHNXzTKM-nVljSRhrfpoBYpPxQSQVTedWIA2Sks4fBvEwdeE60aBRK1HeTps0G1h3RXPYu7q1I5ti9a2axiQtRLA11CxoOvMmnjyWkffi5vyrFKqZ7muQ"
                   {status :status
                    judgement :parsed-body
                    :as response}
                   (get (str "ctia/judgement/" (:short-id judgement-id))
                        :headers {"Authorization" (str "Bearer " jwt-token)
                                  "Origin" "http://external.cisco.com"})]
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
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :severity "High"
                       :confidence "Low"
                       :source "test"
                       :tlp "green"
                       :schema_version schema-version
                       :reason "This is a bad IP address that talked to some evil servers"
                       :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                    :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
                      judgement))
               (is (= expected-headers
                      (select-keys (:headers response)
                                   ["Access-Control-Expose-Headers"
                                    "Access-Control-Allow-Origin"
                                    "Access-Control-Allow-Methods"]))
                   "Should returns the CORS headers even using JWT")))))))))

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
                          :timestamp #inst "2042-01-01T00:00:00.000Z"
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
               :timestamp #inst "2042-01-01T00:00:00.000Z"
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
                          :timestamp #inst "2042-01-01T00:00:00.000Z"
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
               :timestamp #inst "2042-01-01T00:00:00.000Z"
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
                          :timestamp #inst "2042-01-01T00:00:00.000Z"
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
               :timestamp #inst "2042-01-01T00:00:00.000Z"
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
              {:error "Mismatching disposition and dispositon_name for judgement",
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
              {:error "Mismatching disposition and dispositon_name for judgement",
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
        judgement-sort-fields)

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
                          true
                          true))))
