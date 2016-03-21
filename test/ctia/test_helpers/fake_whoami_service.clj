(ns ctia.test-helpers.fake-whoami-service
  (:require [cheshire.core :as json]
            [ctia.auth :as auth]
            [ctia.auth.threatgrid :as threatgrid]
            [schema.core :as s]
            [ctia.auth :as ctia-auth]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params])
  (:import java.net.ServerSocket
           org.eclipse.jetty.server.Server))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Protocols
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
  threatgrid/IWhoAmI
  (whoami [_ token]
    (whoami-fn token))
  IFakeWhoAmIServer
  (start-server [this]
    (reset! server (jetty/run-jetty (-> (make-handler this)
                                        params/wrap-params)
                                    {:port port
                                     :min-threads 6
                                     :max-threads 6
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
   role :- s/Str]
  {"data" {"login" login
           "role" role}})

(s/defn set-whoami-response
  "Meant to be called from code that is wrapped in 'fixture-server'
   because it assumes that FakeWhoAmIService is being used"
  ([token :- s/Str
    login :- s/Str
    role :- s/Str]
   (set-whoami-response token (->whoami-response login role)))
  ([token :- s/Str
    response :- WhoAmIResponse]
   (register-token-response (:whoami-service @auth/auth-service)
                            token
                            response)))

(defn available-port []
  (with-open [sock (ServerSocket. 0)]
    (.getLocalPort sock)))

(defn fixture-server [test]
  (try
    (let [orig-auth-srvc @auth/auth-service]
      (try
        (reset! auth/auth-service (threatgrid/make-auth-service
                                   (make-fake-whoami-service (available-port))
                                   threatgrid/lookup-stored-identity))
        (start-server (:whoami-service @auth/auth-service))
        (test)
        (finally
          (stop-server (:whoami-service @auth/auth-service))
          (reset! auth/auth-service orig-auth-srvc))))))

(defn fixture-reset-state
  "Meant to be triggered inside of fixture-server, eg fixture :once
   fixture-server and fixture :each fixture-reset-state."
  [test]
  (clear-all (:whoami-service @auth/auth-service))
  (test))
