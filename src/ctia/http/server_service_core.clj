(ns ctia.http.server-service-core
  (:require [ctia.http.server :refer [new-jetty-instance]]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [clojure.tools.logging :as log]
            [schema.core :as s])
  (:import [org.eclipse.jetty.server Server]))

(s/defn ^:private server->port :- (s/constrained s/Int pos?)
  [server :- Server]
  (-> server
      .getURI
      .getPort))

(s/defn start [context
               http-config
               services :- APIHandlerServices]
  (let [_ (log/info "Starting HTTP server...")
        server (new-jetty-instance http-config services)
        _ (log/info (str "Started HTTP server on port " (server->port server)))]
    (assoc context
           :services services
           :server server)))

(defn stop [{:keys [^Server server] :as context}]
  (some-> server .stop)
  (dissoc context :server))

(s/defn get-port :- s/Int
  [{:keys [server] :as context}]
  (when-not server
    (throw (ex-info "Server not started!" {})))
  (server->port server))
