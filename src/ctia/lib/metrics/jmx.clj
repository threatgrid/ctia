(ns ctia.lib.metrics.jmx
  (:require [clj-momo.lib.metrics.jmx :as jmx]
            [ctia.properties :as p]))

(defn init! [get-in-config]
  (when (get-in-config [:ctia :metrics :jmx :enabled])
    (jmx/start)))
