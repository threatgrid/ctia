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
  (:import [graphql GraphQL]
           [org.eclipse.jetty.server Server]))

(defn get-graphql [{:keys [graphql]}]
  {:post [(instance? GraphQL %)]}
  graphql)

(s/defn ^:private server->port :- Port
  [server :- Server]
  (-> server
      .getURI
      .getPort))

(s/defn start [context
               http-config
               services :- (-> APIHandlerServices
                               (st/dissoc :CTIAHTTPServerService))]
  (let [_ (log/info "Starting HTTP server...")
        [server graphql] (let [graphql-prm (promise)
                               server-prm (promise)
                               ;; tie the knot for CTIAHTTPServerService self-recursion
                               services (-> services
                                            (assoc-in [:CTIAHTTPServerService :get-port]
                                                      #(server->port @server-prm))
                                            (assoc-in [:CTIAHTTPServerService :get-graphql]
                                                      #(deref graphql-prm)))]
                           (deliver server-prm (new-jetty-instance http-config services))
                           (deliver graphql-prm
                                    (-> (graphql.schemas/graphql services)
                                        (resolve-with-rt-ctx
                                          {:services (APIHandlerServices->RealizeFnServices
                                                       services)})))
                           [@server-prm @graphql-prm])
        _ (log/info (str "Started HTTP server on port " (server->port server)))]
    (assoc context
           :server server
           :graphql graphql)))

(defn stop [{:keys [^Server server] :as context}]
  (some-> server .stop)
  (dissoc context :server :graphql))

(s/defn get-port :- Port
  [{:keys [server] :as context}]
  (when-not server
    (throw (ex-info "Server not started!" {:context context})))
  (server->port server))
