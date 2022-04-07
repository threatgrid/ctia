(ns ctia.task.update-index-state
  (:require [ctia.init :as init]
            [ctia.properties :as p]))

(defn do-task []
  (try (init/start-ctia! {:config (assoc-in (p/build-init-config) [:ctia :store :es :default ::update-index-state-task] true)})
       0
       (catch Throwable _
         1)))

(defn -main [& args]
  (assert (empty? args) "No arguments supported by ctia.task.update-mappings")
  (System/exit (do-task)))
