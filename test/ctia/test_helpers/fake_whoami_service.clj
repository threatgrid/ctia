(ns ctia.test-helpers.fake-whoami-service
  (:require [cheshire.core :as json]
            [ctia.auth.threatgrid :as threatgrid]
            [ctia.test-helpers.core :as helpers-core]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [schema.core :as s])
  (:import org.eclipse.jetty.server.Server))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Protocol
;;

(defprotocol IFakeWhoAmIServer
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

(defn make-handler [fake-whoami-service-promise]
  (fn [request]
    (let [fake-whoami-service @fake-whoami-service-promise
          _ (register-request fake-whoami-service request)
          api-key (get-in request [:params "api_key"])]
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

(tk/defservice fake-whoami-service
  IFakeWhoAmIServer
  []
  (init [_ context]
        (let [this-promise (promise)]
          (assoc context
                 :requests (atom [])
                 :token->response (atom {})
                 :this-promise this-promise
                 :server (jetty/run-jetty (params/wrap-params
                                            (make-handler this-promise))
                                          {;; any available port
                                           :port 0
                                           :min-threads 9
                                           :max-threads 10
                                           :join? false}))))
  (start [this {:keys [this-promise] :as context}]
         ;; deliver after initialization
         (deliver this-promise this)
         context)
  (stop [_ {:keys [^Server server] :as context}]
        (some-> server .stop)
        (dissoc context :requests :token->response :server))
  (get-port [this]
    (let [{:keys [^Server server]} (service-context this)]
      (-> (doto server
            (assert "Server not started"))
          .getURI .getPort)))
  (register-request [this request]
    (let [{:keys [requests]} (service-context this)]
      (swap! requests conj request)))
  (clear-requests [this]
    (let [{:keys [requests]} (service-context this)]
      (reset! requests [])))
  (register-token-response [this token response]
    (let [{:keys [token->response]} (service-context this)]
      (swap! token->response assoc token response)))
  (clear-token-responses [this]
    (let [{:keys [token->response]} (service-context this)]
      (reset! token->response {})))
  (clear-all [this]
    (clear-requests this)
    (clear-token-responses this))
  (known-token? [this token]
    (let [{:keys [token->response]} (service-context this)]
      (contains? @token->response token)))
  (get-response [this token]
    (let [{:keys [token->response]} (service-context this)]
      (get @token->response token))))

(tk/defservice fake-threatgrid-auth-whoami-url-service
  threatgrid/ThreatgridAuthWhoAmIURLService
  [[:IFakeWhoAmIServer get-port]]
  (get-whoami-url [this] (str "http://localhost:" (get-port) "/")))

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
   because it assumes that IFakeWhoAmIService is being used"
  ([app
    token :- s/Str
    login :- s/Str
    group :- s/Str
    role :- s/Str]
   (set-whoami-response app token (->whoami-response login group role)))
  ([app
    token :- s/Str
    response :- WhoAmIResponse]
   (let [_ (assert (app/get-service app :IFakeWhoAmIServer))
         {{:keys [register-token-response]} :IFakeWhoAmIServer} (app/service-graph app)]
     (register-token-response token
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
