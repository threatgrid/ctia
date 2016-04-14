(ns ctia.main
  (:gen-class)
  (:require [ctia.init :refer [init!]]
            [ctia.http.server :as http-server]
            [ctia.properties :refer [properties]]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer [cider-nrepl-handler]]
            [ring.adapter.jetty :as jetty]))

(defn -main
  "Application entry point"
  [& args]
  (init!)
  (let [{nrepl-port :port
         enabled? :enabled} (get-in @properties [:ctia :nrepl])]
    (when (and enabled? nrepl-port)
      (println (str "Starting nREPL server on port " nrepl-port))
      (nrepl-server/start-server :port nrepl-port
                                 :handler cider-nrepl-handler)))
  (let [http-port (get-in @properties [:ctia :http :port])]
    (println (str "Starting HTTP server on port " http-port))
    (http-server/start!)))
