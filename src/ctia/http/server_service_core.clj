(ns ctia.http.server-service-core
  (:require [ctia.http.server :refer [new-jetty-instance]]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [clojure.tools.logging :as log]
            [schema.core :as s])
  (:import [org.eclipse.jetty.server Server]))

(s/defn start [context
               {:keys [port] :as http-config}
               services :- APIHandlerServices]
  (log/info (str "Starting HTTP server on port " port))
  (assoc context
         :services services
         :server (new-jetty-instance http-config services)))

(defn stop [{:keys [^Server server] :as context}]
  (some-> server .stop)
  (dissoc context :server))
