(ns ctia.http.server-service-core
  (:require [ctia.http.server :refer [new-jetty-instance]]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [schema-tools.core :as st])
  (:import [org.eclipse.jetty.server Server]))

(s/defn ^:private server->port :- (s/constrained s/Int pos?)
  [server :- Server]
  (-> server
      .getURI
      .getPort))

(s/defn start [context
               http-config
               services :- (st/dissoc-in APIHandlerServices [:CTIAHTTPServerService :get-port])]
  (let [_ (log/info "Starting HTTP server...")
        server (let [server-prm (promise)
                     services (assoc-in services [:CTIAHTTPServerService :get-port]
                                        #(server->port @server-prm))]
                 (deliver server-prm (new-jetty-instance http-config services))
                 @server-prm)
        _ (log/info (str "Started HTTP server on port " (server->port server)))]
    (assoc context
           :server server)))

(defn stop [{:keys [^Server server] :as context}]
  (some-> server .stop)
  (dissoc context :server))

(s/defn get-port :- s/Int
  [{:keys [server] :as context}]
  (when-not server
    (throw (ex-info "Server not started!" {:context context})))
  (server->port server))
