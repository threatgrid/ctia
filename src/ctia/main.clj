(ns ctia.main
  (:gen-class)
  (:require [ctia.init :refer [start-ctia!]]))

(defn -main
  "Application entry point"
  [& args]
  (start-ctia! :join? true
               :silent? false))
