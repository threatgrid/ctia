(ns ctia.http.server-service-core
  (:require [ctia.graphql.schemas :as graphql.schemas]
            [ctia.http.server :refer [new-jetty-instance]]
            [ctia.schemas.core :refer [APIHandlerServices
                                       APIHandlerServices->RealizeFnServices
                                       Port
                                       resolve-with-rt-ctx]]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [schema-tools.core :as st])
  (:import [org.eclipse.jetty.server Server]))

(s/defn ^:private server->port :- Port
  [server :- Server]
  (-> server
      .getURI
      .getPort))

(s/defn start [context
               http-config
               set-port :- (s/=> Port Port)
               services :- APIHandlerServices]
  (let [_ (log/info "Starting HTTP server...")
        server (new-jetty-instance http-config services)
        port (doto (server->port server)
               set-port)
        _ (log/info (str "Started HTTP server on port " port))]
    (assoc context
           :server server)))

(defn stop [{:keys [^Server server] :as context}]
  (some-> server .stop)
  (dissoc context :server))
