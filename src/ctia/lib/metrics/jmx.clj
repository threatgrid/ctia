(ns ctia.lib.metrics.jmx
  (:require [clj-momo.lib.metrics.jmx :as jmx]
            [ctia.properties :as p]))

(defn init! []
  (when (p/get-in-global-properties [:ctia :metrics :jmx :enabled])
    (jmx/start)))
