(ns ctia.http.server
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clj-http.client :as http]
            [ctia
             [properties :refer [properties]]
             [shutdown :as shutdown]]
            [ctia.auth.jwt :as auth-jwt]
            [ctia.http.handler :as handler]
            [ctia.http.middleware
             [auth :as auth]
             [ratelimit :refer [wrap-rate-limit]]]
            [ctia.lib.riemann :as rie]
            [ring-jwt-middleware.core :as rjwt]
            [ring.adapter.jetty :as jetty]
            [ring.middleware
             [cors :refer [wrap-cors]]
             [params :refer [wrap-params]]
             [reload :refer [wrap-reload]]]
            [clojure.core.memoize :as memo])
  (:import org.eclipse.jetty.server.Server
           (java.util.concurrent TimeoutException)
           (java.net UnknownHostException
                     SocketTimeoutException)))

(defonce server (atom nil))

(defn- allow-origin-regexps
  "take a CORS allowed origin config string
   turn it to a a vec of patterns"
  [origins-str]
  (vec (map re-pattern
            (string/split origins-str #","))))

(defn- str->set-of-keywords
  "take a string with words separated with commas, returns a vec of keywords"
  [s]
  (set (map keyword (string/split s #","))))

(defn parse-external-endpoints
  "take a string of couples separated by : and return an hash-map out of it."
  [s]
  (try
    (when s
      (some->> (string/split s #",")
               (map #(string/split % #"=" 2))
               (into {})))
    (catch Exception e
      (throw (ex-info
              (str "Wrong format for external endpoints."
                   " Use 'i=url1,j=url2' where i, j are issuers."
                   " Check the properties.org file of CTIA repository for some examples.")
              {:bad-string s})))))

(defn _http-get [params url jwt]
  (log/infof "checkin JWT, GET %s" url)
  (http/get url
            (into {:as :json
                   :coerce :always
                   :throw-exceptions false
                   :headers {:Authorization (format "Bearer %s" jwt)}
                   :socket-timeout 2000
                   :connection-timeout 2000}
                  params)))

(defn http-get-fn [n]
  (memo/ttl _http-get :ttl/threshold n))

(defn check-external-endpoints
  "check the status of the JWT (typically revocation) by making an HTTP request.
  Should return [] if the JWT is ok, and a list of error messages if something's wrong."
  [http-get rev-hash-map params jwt {:keys [iss] :as claims}]
  (if-let [check-jwt-url (get rev-hash-map iss)]
    (try
      (let [{:keys [status body]}
            (http-get params check-jwt-url jwt)]
        (when (= status 401)
          (let [{:keys [error_description]
                 :or {error_description "JWT Refused"}} body]
            [error_description])))
      (catch TimeoutException e
        (log/warnf "Couldn't check jwt status due to a call timeout to %s"
                   check-jwt-url)
        [])
      (catch SocketTimeoutException e
        (log/warnf "Couldn't check jwt status due to a call timeout to %s"
                   check-jwt-url)
        [])
      (catch UnknownHostException e
          (log/errorf "The server for checking JWT seems down: %s"
                      check-jwt-url)
        [])
      (catch Exception e
        (log/warnf e "Couldn't check jwt status due to an error calling %s"
                   check-jwt-url)
        []))
    (do
      ;; We are here if the JWT is signed by a trusted source but the issuer
      ;; is not explicitely supported.
      ;; Because it is mostly a consequence to a configuration mistake
      ;; this log is an error and not an info.
      (log/errorf "JWT Issuer %s not recognized. You mostly likely need to change the ctia.http.jwt.http-check.endpoints property"
                  iss)
      ["JWT issuer not supported by this instance."])))

(defn wrap-additional-headers
  "Add additional headers to all requests.

  The handler can override the value of those headers but not remove them.
  "
  [handler headers]
  (fn [req]
    (update (handler req)
            :headers (fn [response-headers]
                       (into headers response-headers)))))

(defn wrap-html-headers
  "Wrap specific headers to HTML content

  The handler can override the headers."
  [handler headers]
  (fn [req]
    (let [response (handler req)]
      (cond-> response
        (some->> (get-in response [:headers "Content-Type"])
                 (re-matches #"(?i).*text/html.*"))
        (update :headers (fn [response-headers]
                           (into headers response-headers)))))))

(defn build-csp
  "Build the Content Security Policy header from the http configuration"
  [{:keys [swagger] :as http-config}]
  (str "default-src 'self';"
       " style-src 'self' 'unsafe-inline';"
       " img-src 'self' data:;"
       " script-src 'self' 'unsafe-inline';"
       " connect-src 'self'"
       (when-let [{:keys [token-url refresh-url]} (:oauth2 swagger)]
         (cond-> ""
           token-url (str " " token-url)
           refresh-url (str " " refresh-url)))
       ";"))

(defn- ^Server new-jetty-instance
  [{:keys [dev-reload
           max-threads
           min-threads
           port
           access-control-allow-origin
           access-control-allow-methods
           jwt
           send-server-version]
    :or {access-control-allow-methods "get,post,put,patch,delete"
         send-server-version false}
    :as http-config}]
  (doto
      (jetty/run-jetty
       (cond-> (handler/api-handler)
         true auth/wrap-authentication

         (:enabled jwt)
         auth-jwt/wrap-jwt-to-ctia-auth

         (:enabled jwt)
         ((rjwt/wrap-jwt-auth-fn
           (merge
            {:pubkey-fn ;; if :public-key-map is nil, will use just :public-key
             (when-let [pubkey-for-issuer-map
                        (auth-jwt/parse-jwt-pubkey-map (:public-key-map jwt))]
               (fn [{:keys [iss] :as claims}]
                 (get pubkey-for-issuer-map iss)))
             :error-handler auth-jwt/jwt-error-handler
             :pubkey-path (:public-key-path jwt)
             :no-jwt-handler rjwt/authorize-no-jwt-header-strategy}

            (let [{:keys [endpoints timeout cache-ttl]}
                       (:http-check jwt)]
              (when-let [external-endpoints (parse-external-endpoints endpoints)]
                {:jwt-check-fn (partial check-external-endpoints
                                        (http-get-fn (or cache-ttl 5000))
                                        external-endpoints
                                        (if timeout
                                          {:socket-timeout timeout
                                           :connection-timeout timeout}
                                          {}))}))
            (when-let [lifetime (:lifetime-in-sec jwt)]
              {:jwt-max-lifetime-in-sec lifetime}))))

         true
         ((rie/wrap-request-logs "http req"))

         access-control-allow-origin
         (wrap-cors :access-control-allow-origin
                    (allow-origin-regexps access-control-allow-origin)
                    :access-control-allow-methods
                    (str->set-of-keywords access-control-allow-methods)
                    :access-control-expose-headers
                    (str "X-Iroh-Version,X-Iroh-Config,X-Ctim-Version,"
                         "X-RateLimit-ORG-Limit,"
                         "X-Content-Type-Options,"
                         "Retry-After,X-Total-Hits,X-Next,X-Previous,X-Sort,Etag,"
                         "X-Frame-Options,X-Content-Type-Options,Content-Security-Policy"))

         true (wrap-additional-headers
               {"X-Content-Type-Options" "nosniff"})
         true (wrap-html-headers
               {"Content-Security-Policy" (build-csp http-config)
                "X-Frame-Options" "DENY"})

         true wrap-params

         dev-reload wrap-reload)
       {:port port
        :min-threads min-threads
        :max-threads max-threads
        :join? false
        :send-server-version? send-server-version})
    (.setStopAtShutdown true)
    (.setStopTimeout (* 1000 10))))

(defn- stop!  []
  (swap! server
         (fn [^Server server]
           (when server
             (.stop server))
           nil)))

(defn start! [& {:keys [join?]
                 :or {join? true}}]
  (let [http-config (get-in @properties [:ctia :http])
        server-instance (new-jetty-instance http-config)]
    (reset! server server-instance)
    (shutdown/register-hook! :http.server stop!)
    (if join?
      (.join server-instance)
      server-instance)))
