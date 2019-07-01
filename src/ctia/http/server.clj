(ns ctia.http.server
  (:require [clojure.string :refer [split]]
            [ctia
             [properties :refer [properties]]
             [shutdown :as shutdown]]
            [ctia.auth.jwt :as auth-jwt]
            [ctia.http.handler :as handler]
            [ctia.http.middleware
             [auth :as auth]
             [ratelimit :refer [wrap-rate-limit]]]
            [ring-jwt-middleware.core :as rjwt]
            [ring.adapter.jetty :as jetty]
            [ring.middleware
             [cors :refer [wrap-cors]]
             [params :refer [wrap-params]]
             [reload :refer [wrap-reload]]])
  (:import org.eclipse.jetty.server.Server))

(defonce server (atom nil))

(defn- allow-origin-regexps
  "take a CORS allowed origin config string
   turn it to a a vec of patterns"
  [origins-str]
  (vec (map re-pattern
            (split origins-str #","))))

(defn- str->set-of-keywords
  "take a string with words separated with commas, returns a vec of keywords"
  [s]
  (set (map keyword (split s #","))))

(defn- new-jetty-instance
  [{:keys [dev-reload
           max-threads
           min-threads
           port
           access-control-allow-origin
           access-control-allow-methods
           jwt
           send-server-version]
    :or {access-control-allow-methods "get,post,put,patch,delete"
         send-server-version false}}]
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
            (when-let [lifetime (:lifetime-in-sec jwt)]
              {:jwt-max-lifetime-in-sec lifetime}))))

         access-control-allow-origin
         (wrap-cors :access-control-allow-origin
                    (allow-origin-regexps access-control-allow-origin)
                    :access-control-allow-methods
                    (str->set-of-keywords access-control-allow-methods)
                    :access-control-expose-headers "X-Total-Hits,X-Next,X-Previous,X-Sort,Etag,X-Ctia-Version,X-Ctia-Config,X-Ctim-Version,X-RateLimit-GROUP-Limit")

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
