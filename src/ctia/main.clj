(ns ctia.main
  (:gen-class))

(defn -main
  "Application entry point"
  [& args]
  ((requiring-resolve 'ctia.init/start-ctia!)
   :join? true
   :silent? false))
