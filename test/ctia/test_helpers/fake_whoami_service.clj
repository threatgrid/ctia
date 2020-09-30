(ns ctia.test-helpers.fake-whoami-service
  (:require [cheshire.core :as json]
            [clj-momo.lib.net :as net]
            [ctia.auth :as auth]
            [ctia.auth.threatgrid :as threatgrid]
            [ctia.test-helpers.core :as helpers-core]
            [schema.core :as s]
            [ctia.auth :as ctia-auth]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params])
  (:import org.eclipse.jetty.server.Server))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Protocol
;;

(defprotocol IFakeWhoAmIServer
  (start-server [this])
  (stop-server [this])
  (register-request [this request])
  (clear-requests [this])
  (register-token-response [this token response])
  (clear-token-responses [this])
  (clear-all [this])
  (known-token? [this token])
  (get-response [this token]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Fake server
;;

(defn make-handler [fake-whoami-service]
  (fn [request]
    (register-request fake-whoami-service request)
    (let [api-key (get-in request [:params "api_key"])]
      (cond
        (nil? api-key)
        {:status 401
         :headers {"Content-type" "application/json"}
         :body (json/generate-string
                {"error" "API key could not be determined"})}

        (not (known-token? fake-whoami-service api-key))
        {:status 403
         :headers {"Content-type" "application/json"}
         :body (json/generate-string
                {"error" "Unknown API key"
                 "api_key" api-key})}

        :else
        (let [{:strs [api_version id]
               :or {api_version 2
                    id 1234567}
               {:strs [role email organization_id name login title]
                :or {role "user"
                     email "foo@example.com"
                     organization_id 1
                     name "Foo User"
                     login "foouser"
                     title "FooBar2016"}} "data"}
              (get-response fake-whoami-service api-key)]
          {:status 200
           :headers {"Content-Type" "application/json"}
           :body (json/generate-string
                  {"api_version" api_version
                   "id" id
                   "data" {"role" role
                           "email" email
                           "organization_id" organization_id
                           "name" name
                           "login" login
                           "title" title}})})))))

(defrecord FakeWhoAmIService [whoami-fn server requests url port token->response]
  IFakeWhoAmIServer
  (start-server [this]
    (reset! server (jetty/run-jetty (params/wrap-params
                                     (make-handler this))
                                    {:port port
                                     :min-threads 1
                                     :max-threads 10
                                     :join? false})))
  (stop-server [_]
    (swap! server (fn [^Server s]
                    (if s (.stop s))
                    nil)))
  (register-request [_ request]
    (swap! requests conj request))
  (clear-requests [_]
    (reset! requests []))
  (register-token-response [_ token response]
    (swap! token->response assoc token response))
  (clear-token-responses [_]
    (reset! token->response {}))
  (clear-all [this]
    (clear-requests this)
    (clear-token-responses this))
  (known-token? [_ token]
    (contains? @token->response token))
  (get-response [_ token]
    (get @token->response token)))

(defn make-fake-whoami-service [port]
  (let [url (str "http://127.0.0.1:" port "/")]
    (map->FakeWhoAmIService
     {:whoami-fn (threatgrid/make-whoami-fn url)
      :server (atom nil)
      :requests (atom [])
      :url url
      :port port
      :token->response (atom {})})))

(defonce fake-whoami-service (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Fixtures & Test Helpers
;;

(s/defschema WhoAmIResponse
  {(s/optional-key "api_version") s/Any
   (s/optional-key "id") s/Any
   (s/required-key "data") {(s/required-key "login") s/Str
                            (s/required-key "role") s/Str
                            (s/optional-key "organization_id") s/Any
                            (s/optional-key "email") s/Any
                            (s/optional-key "name") s/Any
                            (s/optional-key "title") s/Any}})

(s/defn ->whoami-response :- WhoAmIResponse
  [login :- s/Str
   group :- s/Str
   role :- s/Str]
  {"data" {"login" login
           "role" role
           "organization_id" group}})

(s/defn set-whoami-response
  "Meant to be called from code that is wrapped in 'fixture-server'
   because it assumes that FakeWhoAmIService is being used"
  ([token :- s/Str
    login :- s/Str
    group :- s/Str
    role :- s/Str]
   (set-whoami-response token (->whoami-response login group role)))
  ([token :- s/Str
    response :- WhoAmIResponse]
   (register-token-response @fake-whoami-service
                            token
                            response)))

(defn fixture-server
  "Start and stop a fake whoami service. Sets the auth property with the URL to
   the service, so the CTIA instance/HTTP server should be started after this."
  [t]
  (let [port (net/available-port)]
    (reset! fake-whoami-service (make-fake-whoami-service port))
    (try
      (start-server @fake-whoami-service)
      (helpers-core/with-properties
        ["ctia.auth.threatgrid.whoami-url" (str "http://localhost:" port "/")
         "ctia.auth.threatgrid.cache" false
         "ctia.auth.type" "threatgrid"]
        (t))
      (finally
        (stop-server @fake-whoami-service)
        (reset! fake-whoami-service nil)))))

(defn fixture-reset-state
  "May be used inside of fixture-server, eg fixture :once
   fixture-server and fixture :each fixture-reset-state."
  [t]
  (clear-all @fake-whoami-service)
  (t))
