(ns ctia.main
  (:gen-class)
  (:require
   [ctia.http.server :as http-server]
   [ctia.init :refer [log-properties start-ctia!]]))

(defn -main
  "Application entry point"
  [& args]
  (start-ctia! :join? true
               :silent? false))

(defn start []
  (start-ctia! :join? false
               :silent? false))

(defn stop []
  (#'http-server/stop!))
