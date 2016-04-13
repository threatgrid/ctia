(ns ctia.main
  (:gen-class)
  (:require [ctia.init :refer [init!]]
            [ctia.http.server :as http-server]
            [ctia.properties :refer [properties]]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer [cider-nrepl-handler]]
            [ring.adapter.jetty :as jetty]))

(defn start-ctia
  "Does the heavy lifting for -main"
  [& {:keys [join? silent?]}]
  ;; Configure everything
  (init!)

  ;; Start nREPL server
  (let [{nrepl-port :port
         nrepl-enabled? :enabled} (get-in @properties [:ctia :nrepl])]
    (when (and nrepl-enabled? nrepl-port)
      (when-not silent?
        (println (str "Starting nREPL server on port " nrepl-port)))
      (nrepl-server/start-server :port nrepl-port
                                 :handler cider-nrepl-handler)))
  ;; Start HTTP server
  (let [http-port (get-in @properties [:ctia :http :port])]
    (when-not silent?
      (println (str "Starting HTTP server on port " http-port)))
    (http-server/start! :join? join?)))

(defn -main
  "Application entry point"
  [& args]
  (start-ctia :join? true
              :silent? false))
