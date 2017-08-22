(ns ctia.http.server
  (:require [clojure.string :refer [split]]
            [ctia
             [properties :refer [properties]]
             [shutdown :as shutdown]]
            [ctia.auth.jwt :as auth-jwt]
            [ctia.http.middleware.auth :as auth]
            [ctia.http.handler :as handler]
            [ring-jwt-middleware.core :as rjwt]
            [ring.adapter.jetty :as jetty]
            [ring.middleware
             [cors :refer [wrap-cors]]
             [params :refer [wrap-params]]
             [reload :refer [wrap-reload]]])
  (:import org.eclipse.jetty.server.Server))

(defonce server (atom nil))

(def default-allow-methods "get,post,put,delete")

(defn- allow-origin-regexps [origins-str]
  "take a CORS allowed origin config string
   turn it to a a vec of patterns"
  (vec (map re-pattern
            (split origins-str #","))))


(defn- allow-methods [methods-str]
  "take a CORS allowed method config string
   turn it to a a vec of metjod keywords"
  (vec (map keyword (split methods-str #","))))


(defn- new-jetty-instance
  [{:keys [dev-reload
           max-threads
           min-threads
           port
           access-control-allow-origin
           access-control-allow-methods
           jwt]
    :or {access-control-allow-methods "get,post,put,delete"}}]
  (doto
      (jetty/run-jetty
       (cond-> #'handler/api-handler

         access-control-allow-origin
         (wrap-cors :access-control-allow-origin
                    (allow-origin-regexps access-control-allow-origin)
                    :access-control-allow-methods
                    (allow-methods access-control-allow-methods))

         true auth/wrap-authentication

         (:enabled jwt)
         auth-jwt/wrap-jwt-to-ctia-auth

         (:enabled jwt)
         ((rjwt/wrap-jwt-auth-fn {:pubkey-path (:public-key-path jwt)
                                  :no-jwt-handler rjwt/authorize-no-jwt-header-strategy}))

         true wrap-params

         dev-reload wrap-reload)
       {:port port
        :min-threads min-threads
        :max-threads max-threads
        :join? false})
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
