(ns ctia.task.update-index-state
  (:require [ctia.init :as init]
            [ctia.properties :as p]
            [puppetlabs.trapperkeeper.internal :as internal]))

(defn do-task []
  (try (-> {:config (assoc-in (p/build-init-config)
                              [:ctia :store :es :default ::update-index-state-task]
                              true)}
           init/start-ctia!
           internal/shutdown
           count ;; returns number of exceptions thrown
           (min 1)) ;; exit 1 if exceptions, otherwise 0
       (catch Throwable _
         1)))

(defn -main [& args]
  (assert (empty? args) "No arguments supported by ctia.task.update-mappings")
  (System/exit (do-task)))
