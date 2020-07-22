(ns ctia.http.server-service-core
  (:require [ctia.http.server :refer [new-jetty-instance]]
            [clojure.tools.logging :as log])
  (:import [org.eclipse.jetty.server Server]))

(defn start [context {:keys [port] :as http-config}]
  (log/info (str "Starting HTTP server on port " port))
  (assoc context
         :server (new-jetty-instance http-config)))

(defn stop [{:keys [^Server server] :as context}]
  (.stop server)
  (dissoc context :server))
