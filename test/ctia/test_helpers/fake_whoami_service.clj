(ns ctia.test-helpers.fake-whoami-service
  (:require [cheshire.core :as json]
            [clj-momo.lib.net :as net]
            [ctia.auth :as auth]
            [ctia.auth.threatgrid :as threatgrid]
            [ctia.test-helpers.core :as helpers-core]
            [schema.core :as s]
            [ctia.auth :as ctia-auth]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]
            [puppetlabs.trapperkeeper.app :as app])
  (:import org.eclipse.jetty.server.Server))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Protocol
;;

(defprotocol IFakeWhoAmIServer
  (start-server [this port])
  (stop-server [this])
  (get-port [this])
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

(defrecord FakeWhoAmIService [server requests url token->response]
  IFakeWhoAmIServer
  (start-server [this port]
    (swap! server (fn [old]
                    (assert (not old) "Server already started!")
                    (jetty/run-jetty (params/wrap-params
                                       (make-handler this))
                                     {:port port
                                      :min-threads 9
                                      :max-threads 10
                                      :join? false}))))
  (stop-server [_]
    (swap! server (fn [^Server s]
                    (some-> s .stop)
                    nil)))
  (get-port [_]
    (let [^Server s (doto @server
                      (assert "Server not started"))]
      (-> s .getURI .getPort)))
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

(defn make-fake-whoami-service []
  (map->FakeWhoAmIService
    {:server (atom nil)
     :requests (atom [])
     :token->response (atom {})}))

(tk/defservice fake-threatgrid-auth-whoami-url-service
  threatgrid/ThreatgridAuthWhoAmIURLService
  []
  (init [_ context]
        (assoc context :server (doto (make-fake-whoami-service)
                                 (start-server 0))))
  (stop [_ {:keys [server] :as context}]
        (some-> server stop-server)
        (dissoc context :server))
  (get-whoami-url
    [this]
    (let [{:keys [server]} (service-context this)]
      (assert server)
      (str "http://localhost:" (get-port server) "/"))))

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
  ([app
    token :- s/Str
    login :- s/Str
    group :- s/Str
    role :- s/Str]
   (set-whoami-response app token (->whoami-response login group role)))
  ([app
    token :- s/Str
    response :- WhoAmIResponse]
   (let [;; assuming ThreatgridAuthWhoAmIURLService is fake-threatgrid-auth-whoami-url-service
         whoami-service (-> (app/get-service app :ThreatgridAuthWhoAmIURLService)
                            service-context
                            :server)
         _ (assert whoami-service)]
     (register-token-response whoami-service
                              token
                              response))))

(defn fixture-server
  "Start and stop a fake whoami service. Sets the auth property with the URL to
   the service, so the CTIA instance/HTTP server should be started after this."
  [t]
  (helpers-core/with-properties
    [;; fake-threatgrid-auth-whoami-url-service will override this stub
     ;; this after the app has started via ThreatgridAuthWhoAmIURLService's `get-whoami-url`
     "ctia.auth.threatgrid.whoami-url" "http://STUB:0/"
     "ctia.auth.threatgrid.cache" false
     "ctia.auth.type" "threatgrid"]
    (t)))
