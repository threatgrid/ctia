(ns ctia.lib.metrics.jmx
  (:require [clj-momo.lib.metrics.jmx :as jmx]
            [ctia.properties :as p]))

(defn init! []
  (when (get-in (p/read-global-properties) [:ctia :metrics :jmx :enabled])
    (jmx/start)))
