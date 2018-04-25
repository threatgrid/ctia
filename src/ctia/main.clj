(ns ctia.main
  (:gen-class)
  (:require
   [ctia.init :refer [log-properties start-ctia!]]))

(defn -main
  "Application entry point"
  [& args]
  (start-ctia! :join? true
               :silent? false))
