(ns ctia.entity.web-test
  (:require [clj-http.client :as client]
            [clj-jwt.core :as jwt]
            [clj-jwt.key :as jwt-key]
            [clj-momo.lib.clj-time.core :as time]
            [clj-momo.lib.clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clojure.data.json :as json]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [clojure.tools.logging.test :as tlog]
            [clojure.walk :as walk]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers :refer [GET POST]]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.domain.id :as id]
            [ring.adapter.jetty :as jetty]
            [schema.core :as s]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :each
  validate-schemas
  helpers/fixture-properties:cors
  whoami-helpers/fixture-server)

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

(def expected-headers
  {"Access-Control-Expose-Headers"
   "X-Iroh-Version,X-Iroh-Config,X-Ctim-Version,X-RateLimit-ORG-Limit,X-Content-Type-Options,Retry-After,X-Total-Hits,X-Next,X-Previous,X-Sort,Etag,X-Frame-Options,X-Content-Type-Options,Content-Security-Policy",
   "Access-Control-Allow-Origin" "http://external.cisco.com",
   "Access-Control-Allow-Methods" "DELETE, GET, PATCH, POST, PUT"})

(deftest headers-test
  (helpers/fixture-properties:cors
   (fn []
     (test-for-each-store-with-app
      (fn [app]
        (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
        (helpers/set-capabilities! app "baruser" ["bargroup"] "user" #{})
        (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
        (whoami-helpers/set-whoami-response app "2222222222222" "baruser" "bargroup" "user")
        (testing "Headers"
          (let [{judgement :parsed-body
                 status :status
                 :as resp}
                (POST app
                      "ctia/judgement"
                      :body new-judgement-1
                      :headers {"Authorization" "45c1f5e3f05d0"
                                "Origin" "http://external.cisco.com"})
                bad-origin-resp (POST app
                                      "ctia/judgement"
                                      :body new-judgement-1
                                      :headers {"Authorization" "45c1f5e3f05d0"
                                                "Origin" "http://badcors.com"})
                judgement-id (id/long-id->id (:id judgement))
                swagger-ui-resp (GET app
                                     "index.html")]

            (is (= 201 status))
            (is (= expected-headers
                   (select-keys (:headers resp)
                                ["Access-Control-Expose-Headers"
                                 "Access-Control-Allow-Origin"
                                 "Access-Control-Allow-Methods"]))
                "We should return the CORS headers when correct origin")
            (is (= "nosniff" (get-in resp [:headers "X-Content-Type-Options"]))
                "An API request should have the X-Content-Type-Options header set to nosniff")
            (is (nil? (get-in resp [:headers "Content-Security-Policy"]))
                "An API request should not contain the Content-Security-Policy header")
            (is (nil? (get-in resp [:headers "X-Frame-Options"]))
                "An API request should not contain the X-Frame-Options header")
            (is (= 200 (:status swagger-ui-resp)))
            (is (= "nosniff"
                   (get-in swagger-ui-resp [:headers "X-Content-Type-Options"]))
                "Swagger-UI request should contain the X-Content-Type-Options header set to nosniff")
            (is (= "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; script-src 'self' 'unsafe-inline'; connect-src 'self' http://localhost:9001/iroh/oauth2/token http://localhost:9001/iroh/oauth2/refresh;"
                   (get-in swagger-ui-resp [:headers "Content-Security-Policy"]))
                "The request should contain the Content-Security-Policy header set to nosniff")
            (is (= "DENY"
                   (get-in swagger-ui-resp [:headers "X-Frame-Options"]))
                "The request should contain the Content-Security-Policy header set to nosniff")


            (is (= 201 (:status bad-origin-resp)))
            (is (= {}
                   (select-keys (:headers bad-origin-resp)
                                ["Access-Control-Expose-Headers"
                                 "Access-Control-Allow-Origin"
                                 "Access-Control-Allow-Methods"]))
                "We shouldn't return the CORS headers for bad origins")

            (testing "GET /ctia/judgement/:id with bad JWT Authorization header"
              (let [response (GET app
                                  (str "ctia/judgement/" (:short-id judgement-id))
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
              (helpers/fixture-with-fixed-time (tc/to-date (time/date-time 2017 02 15 14 15 10))
                (fn []
                (let [jwt-token "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL3VzZXJcL2VtYWlsIjoiZ2J1aXNzb24rcWFfc2RjX2lyb2hAY2lzY28uY29tIiwiaHR0cHM6XC9cL3NjaGVtYXMuY2lzY28uY29tXC9pcm9oXC9pZGVudGl0eVwvY2xhaW1zXC91c2VyXC9pZHBcL2lkIjoiYW1wIiwiaHR0cHM6XC9cL3NjaGVtYXMuY2lzY28uY29tXC9pcm9oXC9pZGVudGl0eVwvY2xhaW1zXC91c2VyXC9uaWNrIjoiZ2J1aXNzb24rcWFfc2RjX2lyb2hAY2lzY28uY29tIiwiZW1haWwiOiJnYnVpc3NvbitxYV9zZGNfaXJvaEBjaXNjby5jb20iLCJzdWIiOiI1NmJiNWY4Yy1jYzRlLTRlZDMtYTkxYS1jNjYwNDI4N2ZlMzIiLCJpc3MiOiJJUk9IIEF1dGgiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL3Njb3BlcyI6WyJjYXNlYm9vayIsImdsb2JhbC1pbnRlbCIsInByaXZhdGUtaW50ZWwiLCJjb2xsZWN0IiwiZW5yaWNoIiwiaW5zcGVjdCIsImludGVncmF0aW9uIiwiaXJvaC1hdXRoIiwicmVzcG9uc2UiLCJ1aS1zZXR0aW5ncyJdLCJleHAiOjE0ODc3NzI4NTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvb2F1dGhcL2NsaWVudFwvbmFtZSI6Imlyb2gtdWkiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29yZ1wvaWQiOiI2MzQ4OWNmOS01NjFjLTQ5NTgtYTEzZC02ZDg0YjdlZjA5ZDQiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29yZ1wvbmFtZSI6IklST0ggVGVzdGluZyIsImp0aSI6ImEyNjhhZTdhMy0wOWM5LTQxNDktYjQ5NS1iOThjOGM1ZGU2NjYiLCJuYmYiOjE0ODcxNjc3NTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvdXNlclwvaWQiOiI1NmJiNWY4Yy1jYzRlLTRlZDMtYTkxYS1jNjYwNDI4N2ZlMzIiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29hdXRoXC9jbGllbnRcL2lkIjoiaXJvaC11aSIsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvdmVyc2lvbiI6IjEiLCJpYXQiOjE0ODcxNjgwNTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvb2F1dGhcL2tpbmQiOiJzZXNzaW9uLXRva2VuIn0.jl0r3LiL6qOy6DIDZs5NRiQBHlJEzXFXUvKXGPd2PL66xSE0v0Bkc6FD3vPccYxvk-tWBMJX8oiDuAgYt2eRU05blPtzy1yQ-V-zJtxnpuQbDzvVytZvE9n1_8NdvcLa9eXBjUkJ2FsXAIguXpVDIbR3zs9MkjfyrsKeVCmhC3QTehj55Rf-WINeTq0UflIyoZqfK5Mewl-DBwbvTRjTIRJpNPhjErJ0ypHNXzTKM-nVljSRhrfpoBYpPxQSQVTedWIA2Sks4fBvEwdeE60aBRK1HeTps0G1h3RXPYu7q1I5ti9a2axiQtRLA11CxoOvMmnjyWkffi5vyrFKqZ7muQ"
                      {judgement :parsed-body
                       :as response}
                      (GET app
                           (str "ctia/judgement/" (:short-id judgement-id))
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
                         (dissoc judgement :owner :groups :created :modified)))
                  (is (= expected-headers
                         (select-keys (:headers response)
                                      ["Access-Control-Expose-Headers"
                                       "Access-Control-Allow-Origin"
                                       "Access-Control-Allow-Methods"]))
                      "Should return the CORS headers when using valid JWT")))))))))

(deftest test-judgement-with-jwt-routes
  (test-for-each-store-with-app
   (fn [app]
     (testing "POST /ctia/judgement with JWT Authorization header."
       (helpers/fixture-with-fixed-time (tc/to-date (time/date-time 2017 02 15 14 15 10))
         (fn []
           (let [jwt-token "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL3VzZXJcL2VtYWlsIjoiZ2J1aXNzb24rcWFfc2RjX2lyb2hAY2lzY28uY29tIiwiaHR0cHM6XC9cL3NjaGVtYXMuY2lzY28uY29tXC9pcm9oXC9pZGVudGl0eVwvY2xhaW1zXC91c2VyXC9pZHBcL2lkIjoiYW1wIiwiaHR0cHM6XC9cL3NjaGVtYXMuY2lzY28uY29tXC9pcm9oXC9pZGVudGl0eVwvY2xhaW1zXC91c2VyXC9uaWNrIjoiZ2J1aXNzb24rcWFfc2RjX2lyb2hAY2lzY28uY29tIiwiZW1haWwiOiJnYnVpc3NvbitxYV9zZGNfaXJvaEBjaXNjby5jb20iLCJzdWIiOiI1NmJiNWY4Yy1jYzRlLTRlZDMtYTkxYS1jNjYwNDI4N2ZlMzIiLCJpc3MiOiJJUk9IIEF1dGgiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL3Njb3BlcyI6WyJjYXNlYm9vayIsImdsb2JhbC1pbnRlbCIsInByaXZhdGUtaW50ZWwiLCJjb2xsZWN0IiwiZW5yaWNoIiwiaW5zcGVjdCIsImludGVncmF0aW9uIiwiaXJvaC1hdXRoIiwicmVzcG9uc2UiLCJ1aS1zZXR0aW5ncyJdLCJleHAiOjE0ODc3NzI4NTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvb2F1dGhcL2NsaWVudFwvbmFtZSI6Imlyb2gtdWkiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29yZ1wvaWQiOiI2MzQ4OWNmOS01NjFjLTQ5NTgtYTEzZC02ZDg0YjdlZjA5ZDQiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29yZ1wvbmFtZSI6IklST0ggVGVzdGluZyIsImp0aSI6ImEyNjhhZTdhMy0wOWM5LTQxNDktYjQ5NS1iOThjOGM1ZGU2NjYiLCJuYmYiOjE0ODcxNjc3NTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvdXNlclwvaWQiOiI1NmJiNWY4Yy1jYzRlLTRlZDMtYTkxYS1jNjYwNDI4N2ZlMzIiLCJodHRwczpcL1wvc2NoZW1hcy5jaXNjby5jb21cL2lyb2hcL2lkZW50aXR5XC9jbGFpbXNcL29hdXRoXC9jbGllbnRcL2lkIjoiaXJvaC11aSIsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvdmVyc2lvbiI6IjEiLCJpYXQiOjE0ODcxNjgwNTAsImh0dHBzOlwvXC9zY2hlbWFzLmNpc2NvLmNvbVwvaXJvaFwvaWRlbnRpdHlcL2NsYWltc1wvb2F1dGhcL2tpbmQiOiJzZXNzaW9uLXRva2VuIn0.jl0r3LiL6qOy6DIDZs5NRiQBHlJEzXFXUvKXGPd2PL66xSE0v0Bkc6FD3vPccYxvk-tWBMJX8oiDuAgYt2eRU05blPtzy1yQ-V-zJtxnpuQbDzvVytZvE9n1_8NdvcLa9eXBjUkJ2FsXAIguXpVDIbR3zs9MkjfyrsKeVCmhC3QTehj55Rf-WINeTq0UflIyoZqfK5Mewl-DBwbvTRjTIRJpNPhjErJ0ypHNXzTKM-nVljSRhrfpoBYpPxQSQVTedWIA2Sks4fBvEwdeE60aBRK1HeTps0G1h3RXPYu7q1I5ti9a2axiQtRLA11CxoOvMmnjyWkffi5vyrFKqZ7muQ"
               bearer (str "Bearer " jwt-token)
               jwt-client-id "iroh-ui"
               {judgement :parsed-body
                status :status}
               (POST app
                     "ctia/judgement"
                     :body new-judgement-1
                     :headers {"Authorization" bearer})
               _ (is (= 201 status))
               judgement-id (id/long-id->id (:id judgement))
               expected-get-res
               {:id (id/long-id judgement-id)
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
                :client_id jwt-client-id,
                :owner "56bb5f8c-cc4e-4ed3-a91a-c6604287fe32",
                :groups ["63489cf9-561c-4958-a13d-6d84b7ef09d4"]
                :schema_version schema-version
                :reason "This is a bad IP address that talked to some evil servers"
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}}]
           (let [{:keys [parsed-body status]}
                 (GET app
                      (str "ctia/judgement/" (:short-id judgement-id))
                      :headers {"Authorization" bearer})]
             (is (= 200 status))
             (is (= (dissoc expected-get-res :created :modified) (dissoc parsed-body :created :modified))))

           (testing "Search must properly filter on client-id."
             (let [search-by-client #(GET app
                                          (str "ctia/judgement/search?query=client_id:" %)
                                          :headers {"Authorization" bearer})
                   matched (search-by-client jwt-client-id)
                   not-matched (search-by-client "does not exist")]
               (is (= 200 (:status matched) (:status not-matched)))
               (is (= [expected-get-res] (map #(dissoc % :created :modified) (:parsed-body matched))))
               (is (= [] (:parsed-body not-matched)))))

           (testing "Normal users are not allowed to set the ids during creation."
             (let [response
                   (POST app
                         "ctia/judgement"
                         :body (assoc new-judgement-1
                                      :id "http://localhost:3001/ctia/judgement/judgement-00001111-0000-1111-2222-000011112222")
                         :headers {"Authorization" bearer
                                   "origin" "http://external.cisco.com"})]
               (is (= 403 (:status response)))))

           (testing "POST /ctia/judgement/:id with bad JWT Authorization header"
             (let [response
                   (POST app
                         "ctia/judgement"
                         :body new-judgement-1
                         :headers {"Authorization" "Bearer 45c1f5e3f05d0"
                                   "origin" "http://external.cisco.com"})]
               (is (= 401 (:status response))))))))))))

(def default-jwt-scopes
  ["casebook" "global-intel" "private-intel" "collect"
   "enrich" "inspect" "integration" "iroh-auth"
   "response" "ui-settings"])

(defn gen-jwts
  ([] (gen-jwts default-jwt-scopes))
  ([scopes]
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
          (clm "scopes") (vec scopes),
          (clm "user/email") "gbuisson+qa_sdc_iroh@cisco.com",
          (clm "user/id") "56bb5f8c-cc4e-4ed3-a91a-c6604287fe32",
          (clm "user/idp/id") "amp",
          (clm "user/nick") "gbuisson+qa_sdc_iroh@cisco.com",
          (clm "version") "1"}
         claims-2 (assoc claims-1 :iss "IROH Auth TEST")]
     {:jwt-1 (-> claims-1 jwt/jwt (jwt/sign :RS256 priv-key-1) jwt/to-str)
      :bad-iss-jwt-1 (-> claims-1 (assoc :iss "IROH Auth TEST") jwt/jwt (jwt/sign :RS256 priv-key-1) jwt/to-str)
      :jwt-2 (-> claims-2 jwt/jwt (jwt/sign :RS256 priv-key-2) jwt/to-str)
      :bad-iss-jwt-2 (-> claims-2 (assoc :iss "IROH Auth") jwt/jwt (jwt/sign :RS256 priv-key-2) jwt/to-str)})))

(defn jwt-for-org
  "Signs a JWT identical to gen-jwts/jwt-1 but with a different org/id claim
  and a distinct user id. Used to assert that a caller from another org
  cannot overwrite documents owned by the original org."
  ([org-id] (jwt-for-org org-id default-jwt-scopes))
  ([org-id scopes]
   (let [clm (fn [k] (str "https://schemas.cisco.com/iroh/identity/claims/" k))
         priv-key-1 (jwt-key/private-key "resources/cert/ctia-jwt.key")
         now (t/now)
         in-one-hour (t/plus now (t/hours 1))
         other-user-id "deadbeef-dead-beef-dead-beefdeadbeef"
         claims
         {:exp in-one-hour
          :iat now
          :iss "IROH Auth"
          :email "other-org@example.com"
          :nbf now
          "sub" other-user-id
          :jti "00000000-0000-0000-0000-000000000001"
          (clm "oauth/client/id") "iroh-ui"
          (clm "oauth/client/name") "iroh-ui"
          (clm "oauth/kind") "session-token"
          (clm "org/id") org-id
          (clm "org/name") "Other Org"
          (clm "scopes") (vec scopes)
          (clm "user/email") "other-org@example.com"
          (clm "user/id") other-user-id
          (clm "user/idp/id") "amp"
          (clm "user/nick") "other-org@example.com"
          (clm "version") "1"}]
     (-> claims jwt/jwt (jwt/sign :RS256 priv-key-1) jwt/to-str))))

(s/defn apply-fixtures-with-app
  [properties f-with-app :- (s/=> s/Any (s/=> s/Any (s/named s/Any 'app)))]
  (let [fixture-fn
        (join-fixtures [helpers/fixture-log
                        helpers/fixture-properties:cors
                        #(helpers/with-properties
                           (conj properties "ctia.auth.type" "allow-all")
                           (%))
                        es-helpers/fixture-properties:es-store
                        helpers/fixture-ctia
                        es-helpers/fixture-delete-store-indexes])]
    (fixture-fn
      (fn []
        (f-with-app
          (helpers/get-current-app))))))

(deftest jwt-keymap-tests-on-judgements
  (testing "JWT Key Map"
    (apply-fixtures-with-app
     ["ctia.http.jwt.enabled" true
      "ctia.http.jwt.public-key-map"
      "IROH Auth=resources/cert/ctia-jwt.pub,IROH Auth TEST=resources/cert/ctia-jwt-2.pub"]
     (fn [app]
       (let [{:keys [jwt-1 bad-iss-jwt-1 jwt-2 bad-iss-jwt-2]} (gen-jwts)]
         (testing "POST /ctia/judgement"
           (let [{judgement :parsed-body
                  status :status}
                 (POST app
                       "ctia/judgement"
                       :body new-judgement-1
                       :headers {"Authorization" "45c1f5e3f05d0"
                                 "origin" "http://external.cisco.com"})
                 judgement-id (id/long-id->id (:id judgement))
                 get-judgement (fn [jwt]
                                 (let [response
                                       (client/get
                                        (str "http://localhost:"
                                             (helpers/get-http-port app)
                                             "/ctia/judgement/"
                                             (:short-id judgement-id))
                                        {:headers {"Authorization" (str "Bearer " jwt)}
                                         :throw-exceptions false
                                         :as :json})]
                                   (:status response)))]
             (is (= 201 status))
             (testing "GET /ctia/judgement/:id with bad JWT Authorization header"
               (let [response
                     (GET app
                          (str "ctia/judgement/" (:short-id judgement-id))
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
                   (is (= 401 (get-judgement bad-iss-jwt-2)))))))))))))

(def test-timeout 300)
(def test-cache-ttl 300)

(defn jwt-http-checks-test
  [url-1 url-2 tst-fn]
  (testing "JWT Key Map + Http-Check"
    (apply-fixtures-with-app
     ["ctia.http.jwt.enabled" true
      "ctia.http.jwt.public-key-map"
      "IROH Auth=resources/cert/ctia-jwt.pub,IROH Auth TEST=resources/cert/ctia-jwt-2.pub"

      "ctia.http.jwt.http-check.endpoints" (str "IROH Auth=" url-1 ",IROH Auth TEST=" url-2)
      "ctia.http.jwt.http-check.timeout" test-timeout
      "ctia.http.jwt.http-check.cache-ttl" test-cache-ttl]
     (fn [app]
       (let [jwts (gen-jwts)
             {judgement :parsed-body status :status}
             (POST app
                   "ctia/judgement"
                   :body new-judgement-1
                   :headers {"Authorization" "45c1f5e3f05d0"
                             "origin" "http://external.cisco.com"})
             judgement-id (id/long-id->id (:id judgement))
             get-judgement (fn get-judgement
                             ([jwt] (get-judgement jwt {}))
                             ([jwt get-opts]
                              (client/get
                                (str "http://localhost:"
                                     (helpers/get-http-port app)
                                     "/ctia/judgement/"
                                     (:short-id judgement-id))
                                (into {:throw-exceptions false
                                       :as :json}
                                      (-> get-opts
                                          (update :headers #(into {"Authorization" (str "Bearer " jwt)} %)))))))
             ctx (assoc jwts :get-judgement get-judgement)]
           (is (= 201 status))
           (tst-fn ctx))))))

(defn with-server
  "Start tst-fn function while handler is started as a server.
  The tst-fn should be a function that takes the port as parameter."
  [handler tst-fn]
  (let [s (jetty/run-jetty handler {:port 0
                                    :join? false
                                    :min-threads 2})]
    (try
      (tst-fn (-> s .getURI .getPort))
      (finally
        (.stop s)))))

(deftest jwt-http-checks-server-down-test
  (helpers/fixture-with-fixed-time (tc/to-date (time/date-time 2017 02 15 14 15 10))
    (fn []
      (let [url-1 "https://jwt.check-1/check"
            url-2 "https://jwt.check-2/check"]
        (jwt-http-checks-test
         url-1
         url-2
         (fn [{:keys [get-judgement jwt-1 jwt-2]}]
           (testing "Check URL server down"
             (testing "issuer 1"
               (is (= 200 (:status (get-judgement jwt-1)))
                   "JWT is accepted if the server is down or cannot be found.")
               (is (tlog/logged? "ctia.http.server"
                                 :error
                                 (str "The server for checking JWT seems down: " url-1))))
             (testing "issuer 2"
               (is (= 200 (:status (get-judgement jwt-2)))
                   "JWT is accepted if the server is down or cannot be found.")
               (is (tlog/logged? "ctia.http.server"
                                 :error
                                 (str "The server for checking JWT seems down: " url-2))))))))))

(deftest jwt-http-checks-server-refused-test
  (helpers/fixture-with-fixed-time (tc/to-date (time/date-time 2017 02 15 14 15 10))
    (fn []
      (let [refuse-handler (fn [req]
                             {:status 401
                              :headers {"Content-Type" "application/json"}
                              :body (json/write-str
                                     {:error "refused"
                                      :error_description (format "SERVER ERROR DESCRIPTION: %s"
                                                                 (get-in req [:headers "authorization"]))})})]
        (with-server
          refuse-handler
          (fn [port]
            (let [url-1 (format "http://127.0.0.1:%d/check" port)
                  url-2 (format "http://127.0.0.1:%d/check" port)]
              (jwt-http-checks-test
               url-1
               url-2
               (fn [{:keys [get-judgement jwt-1]}]
                 (testing "Check URL server returns 401"
                   (doseq [;; regression tests for https://github.com/threatgrid/iroh/issues/4541
                           as [:json :edn]
                           :let [{:keys [status body] :as response} (get-judgement jwt-1
                                                                                   {:as as
                                                                                    :headers {"Accept"
                                                                                              (case as
                                                                                                :json "application/json"
                                                                                                :edn "application/edn")}})]]
                 (testing (prn-str as)
                   (is (= 401 status) response)
                   (testing (prn-str body)
                     (is (= {:error ((case as :json name :edn identity) :invalid_jwt)
                             :error_description
                             (str "(56bb5f8c-cc4e-4ed3-a91a-c6604287fe32) SERVER ERROR DESCRIPTION: Bearer "
                                  jwt-1)}
                            ;; regression test
                            ((case as
                               :json (comp walk/keywordize-keys json/read-str)
                               :edn read-string)
                             body))
                         "The error should use the description returned by the server and we check the server get the correct header"))))))))))))))

(deftest jwt-http-checks-server-slow-test
  (helpers/fixture-with-fixed-time (tc/to-date (time/date-time 2017 02 15 14 15 10))
    (fn []
      (let [slow-handler (fn [req]
                           (Thread/sleep 3000)
                           {:status 401
                            :headers {"Content-Type" "application/json"}
                            :body (json/write-str
                                   {:error "refused"
                                    :error_description (get-in req [:headers "Authorization"])})})]
        (with-server
          slow-handler
          (fn [port]
            (let [url-1 (format "http://127.0.0.1:%d/check" port)
                  url-2 (format "http://127.0.0.1:%d/check" port)]
              (jwt-http-checks-test
               url-1
               url-2
               (fn [{:keys [get-judgement jwt-1]}]
                 (testing "Check URL server too long to answer"
                   (is (= 200 (:status (get-judgement jwt-1)))
                       "JWT is accepted if the server timeout")
                   (is (tlog/logged? "ctia.http.server"
                                     :warn
                                     (format "Couldn't check jwt status due to a call timeout to %s" url-1)))))))))))))

(deftest jwt-http-checks-server-cached-response-test
  (helpers/fixture-with-fixed-time (tc/to-date (time/date-time 2017 02 15 14 15 10))
    (fn []
      (let [counter (atom 0)
        count-handler
        (fn [req]
          (swap! counter inc)
          {:status 401
           :headers {"Content-Type" "application/json"}
           :body (json/write-str
                  {:error "refused"
                   :error_description (format "SERVER ERROR DESCRIPTION: %s"
                                              (get-in req [:headers "authorization"]))})})]
    (with-server
      count-handler
      (fn [port]
        (let [url-1 (format "http://127.0.0.1:%d/check" port)
              url-2 (format "http://127.0.0.1:%d/check" port)]
          (jwt-http-checks-test
           url-1
           url-2
           (fn [{:keys [get-judgement jwt-1 jwt-2]}]
             (testing "Check URL cache"
               (is (= 401 (:status (get-judgement jwt-1))))
               (is (= 401 (:status (get-judgement jwt-1))))
               (is (= 401 (:status (get-judgement jwt-1))))
               (is (= 1 @counter)
                   "Multiple call for the same jwt in a short period of time should only generate one call")
               (is (= 401 (:status (get-judgement jwt-2))))
               (is (= 401 (:status (get-judgement jwt-2))))
               (is (= 2 @counter)
                   "Making a call to another JWT should generate another call")
               (assert (= test-cache-ttl ((helpers/current-get-in-config-fn)
                                           [:ctia :http :jwt :http-check :cache-ttl]))
                       ((helpers/current-get-in-config-fn)
                         [:ctia :http :jwt :http-check :cache-ttl]))
               (Thread/sleep (+ test-cache-ttl 100)) ;; wait 100ms more than cache-ttl
               (is (= 401 (:status (get-judgement jwt-1))))
               (is (= 401 (:status (get-judgement jwt-1))))
               (is (= 401 (:status (get-judgement jwt-1))))
               (is (= 3 @counter)
                   "After the cache-ttl we should make a new call for the same JWT")))))))))))))
))

(deftest bundle-import-with-jwt-specify-id-test
  ;; End-to-end check that a JWT carrying the dedicated `ctia-specify-id:write`
  ;; scope can supply entity ids on POST /ctia/bundle/import. The scope confers
  ;; the :specify-id capability via the mapping in ctia.auth.jwt; the flow-layer
  ;; gate in ctia.flows.crud/find-create-entity-id then permits the caller-
  ;; supplied id rather than minting a fresh UUID.
  (test-for-each-store-with-app
   (fn [app]
     (helpers/fixture-with-fixed-time (tc/to-date (time/date-time 2017 02 15 14 15 10))
       (fn []
         (let [scopes (conj default-jwt-scopes "ctia-specify-id:write")
               {:keys [jwt-1]} (gen-jwts scopes)
               bearer (str "Bearer " jwt-1)
               host (str "http://localhost:" (helpers/get-http-port app))
               supplied-short-id "sighting-00112233-4455-6677-8899-aabbccddeeff"
               supplied-id (str host "/ctia/sighting/" supplied-short-id)
               bundle {:type "bundle"
                       :source "specify-id-jwt-test"
                       :sightings [{:id supplied-id
                                    :external_ids ["specify-id-jwt-test/sighting/1"]
                                    :description "caller-supplied id"
                                    :timestamp #inst "2017-02-15T14:15:10.000Z"
                                    :observed_time {:start_time #inst "2017-02-15T14:15:10.000Z"}
                                    :schema_version schema-version
                                    :count 1
                                    :source "specify-id-jwt-test"
                                    :sensor "endpoint.sensor"
                                    :confidence "High"}]}
               {:keys [status parsed-body]}
               (POST app
                     "ctia/bundle/import"
                     :body bundle
                     :headers {"Authorization" bearer})]
           (is (= 200 status)
               "bundle import succeeds when caller supplies :id with ctia-specify-id:write scope")
           (let [sighting-result (first (filter #(= :sighting (:type %))
                                                (:results parsed-body)))]
             (is (= "created" (:result sighting-result))
                 "sighting was created")
             (is (= supplied-id (:id sighting-result))
                 "the caller-supplied :id is honored")
             (testing "the entity is retrievable by the supplied short-id"
               (let [get-response (GET app
                                       (str "ctia/sighting/" supplied-short-id)
                                       :headers {"Authorization" bearer})]
                 (is (= 200 (:status get-response)))
                 (is (= supplied-id (-> get-response :parsed-body :id)))))
             (testing "RED: cross-org overwrite via caller-supplied :id MUST be prevented"
               ;; Safety property: one org must never be able to mutate a
               ;; document owned by another org. The JWT's org/id claim
               ;; populates the entity's :groups; access control on update is
               ;; enforced by allow-write? against owner/groups (see
               ;; ctia.stores.es.crud/handle-update). The create path does NOT
               ;; perform that check (handle-create ignores _ident and writes
               ;; via ductile.doc/bulk-index-docs, an upsert), so a second
               ;; caller from another org can replace the original record by
               ;; reusing its :id.
               ;;
               ;; This test asserts the safe outcome and SHOULD FAIL until a
               ;; flow-layer pre-existence + allow-write? guard is added.
               (let [other-org-jwt (jwt-for-org "ffffffff-ffff-ffff-ffff-ffffffffffff" scopes)
                     other-bearer (str "Bearer " other-org-jwt)
                     attack-bundle (-> bundle
                                       (assoc-in [:sightings 0 :description]
                                                 "OVERWRITE ATTEMPT")
                                       (assoc-in [:sightings 0 :external_ids]
                                                 ["specify-id-jwt-test/sighting/from-other-org"]))
                     attack-response (POST app
                                           "ctia/bundle/import"
                                           :body attack-bundle
                                           :headers {"Authorization" other-bearer})
                     get-after (GET app
                                    (str "ctia/sighting/" supplied-short-id)
                                    :headers {"Authorization" bearer})
                     attack-result (first (filter #(= :sighting (:type %))
                                                  (-> attack-response :parsed-body :results)))]
                 (is (not= "OVERWRITE ATTEMPT"
                           (-> get-after :parsed-body :description))
                     "Org A's record must NOT be modified by another org")
                 (is (= "caller-supplied id"
                        (-> get-after :parsed-body :description))
                     "Org A's original description is intact")
                 (is (not= "created" (:result attack-result))
                     "the cross-org import of an :id owned by another org must NOT be reported as a successful create")))
             (testing "same-org caller reusing their own :id is rejected on the create path"
               ;; Updates have their own endpoint (PUT /ctia/:entity/:id), gated
               ;; by allow-write? in handle-update. The create path should not
               ;; double as an update path: a second create with an existing
               ;; :id must surface a conflict, not silently upsert.
               (let [reuse-bundle (-> bundle
                                      (assoc-in [:sightings 0 :description]
                                                "SAME-ORG SECOND CREATE")
                                      (assoc-in [:sightings 0 :external_ids]
                                                ["specify-id-jwt-test/sighting/reuse"]))
                     reuse-response (POST app
                                          "ctia/bundle/import"
                                          :body reuse-bundle
                                          :headers {"Authorization" bearer})
                     reuse-result (first (filter #(= :sighting (:type %))
                                                 (-> reuse-response :parsed-body :results)))
                     get-after (GET app
                                    (str "ctia/sighting/" supplied-short-id)
                                    :headers {"Authorization" bearer})]
                 (is (not= "created" (:result reuse-result))
                     "a same-org second create on an existing :id must NOT be reported as created")
                 (is (= "caller-supplied id"
                        (-> get-after :parsed-body :description))
                     "the original same-org record is not overwritten by a second create")))
             (testing "brand-new caller-supplied :id still succeeds (regression)"
               ;; Sanity: the collision guard must not break the happy path of
               ;; caller-supplied ids on a fresh id.
               (let [fresh-short-id "sighting-99887766-5544-3322-1100-ffeeddccbbaa"
                     fresh-id (str host "/ctia/sighting/" fresh-short-id)
                     fresh-bundle (-> bundle
                                      (assoc-in [:sightings 0 :id] fresh-id)
                                      (assoc-in [:sightings 0 :external_ids]
                                                ["specify-id-jwt-test/sighting/fresh"]))
                     fresh-response (POST app
                                          "ctia/bundle/import"
                                          :body fresh-bundle
                                          :headers {"Authorization" bearer})
                     fresh-result (first (filter #(= :sighting (:type %))
                                                 (-> fresh-response :parsed-body :results)))]
                 (is (= "created" (:result fresh-result))
                     "a brand-new caller-supplied :id on the create path still succeeds")
                 (is (= fresh-id (:id fresh-result))
                     "the fresh caller-supplied :id is honored"))))))))))

(deftest single-entity-post-with-caller-supplied-id-test
  ;; Same safety properties as bundle-import-with-jwt-specify-id-test, but
  ;; routed through the single-entity POST /ctia/:entity path. Both routes
  ;; share ctia.flows.crud/create-flow, so a fix at the flow layer should
  ;; protect both. This direct test pins the property at the single-entity
  ;; surface so a future refactor that bypasses the flow layer here can't
  ;; silently regress.
  (test-for-each-store-with-app
   (fn [app]
     (helpers/fixture-with-fixed-time (tc/to-date (time/date-time 2017 02 15 14 15 10))
       (fn []
         (let [scopes (conj default-jwt-scopes "ctia-specify-id:write")
               {:keys [jwt-1]} (gen-jwts scopes)
               bearer (str "Bearer " jwt-1)
               host (str "http://localhost:" (helpers/get-http-port app))
               supplied-short-id "sighting-aa112233-4455-6677-8899-aabbccddee01"
               supplied-id (str host "/ctia/sighting/" supplied-short-id)
               new-sighting {:id supplied-id
                             :external_ids ["single-post-test/sighting/1"]
                             :description "original"
                             :timestamp #inst "2017-02-15T14:15:10.000Z"
                             :observed_time {:start_time #inst "2017-02-15T14:15:10.000Z"}
                             :schema_version schema-version
                             :count 1
                             :source "single-post-test"
                             :sensor "endpoint.sensor"
                             :confidence "High"}
               first-resp (POST app
                                "ctia/sighting"
                                :body new-sighting
                                :headers {"Authorization" bearer})]
           (is (= 201 (:status first-resp))
               "first POST with caller-supplied :id succeeds")
           (is (= supplied-id (-> first-resp :parsed-body :id))
               "the caller-supplied :id is honored")
           (testing "same-org second POST with the same :id is rejected (no silent upsert)"
             (let [reuse-resp (POST app
                                    "ctia/sighting"
                                    :body (assoc new-sighting :description "OVERWRITE ATTEMPT")
                                    :headers {"Authorization" bearer})
                   get-after (GET app
                                  (str "ctia/sighting/" supplied-short-id)
                                  :headers {"Authorization" bearer})]
               (is (not= 201 (:status reuse-resp))
                   "second POST with same caller-supplied :id must NOT report 201 Created")
               (is (= "original" (-> get-after :parsed-body :description))
                   "the original record is not overwritten by the second POST")))
           (testing "cross-org POST with another org's :id is rejected"
             (let [other-org-jwt (jwt-for-org "ffffffff-ffff-ffff-ffff-fffffffffff0" scopes)
                   other-bearer (str "Bearer " other-org-jwt)
                   attack-resp (POST app
                                     "ctia/sighting"
                                     :body (assoc new-sighting :description "CROSS-ORG OVERWRITE")
                                     :headers {"Authorization" other-bearer})
                   get-after (GET app
                                  (str "ctia/sighting/" supplied-short-id)
                                  :headers {"Authorization" bearer})]
               (is (not= 201 (:status attack-resp))
                   "cross-org POST with an :id owned by another org must NOT report 201 Created")
               (is (= "original" (-> get-after :parsed-body :description))
                   "Org A's original record is not overwritten by Org B's POST")))))))))

