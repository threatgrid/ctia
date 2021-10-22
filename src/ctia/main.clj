(ns ctia.main
  (:gen-class))

(defn -main
  "Application entry point"
  [& _args]
  (let [app ((requiring-resolve 'ctia.init/start-ctia!))]
    ;; join with TK thread
    ((requiring-resolve 'puppetlabs.trapperkeeper.core/run-app) app)))
