(ns ctia.task.update-index-state
  (:require [ctia.init :as init]
            [ctia.properties :as p]
            [puppetlabs.trapperkeeper.app :as app]))

(defn do-task [config]
  (try (-> {:config (assoc-in config [:ctia :task :ctia.task.update-index-state] true)}
           init/start-ctia!
           (app/stop true)) ;; throws on error
       0
       (catch Throwable e 
         (prn e)
         1)))

(defn -main [& args]
  (assert (empty? args) "No arguments supported by ctia.task.update-mappings")
  (-> (p/build-init-config)
      do-task
      System/exit))
