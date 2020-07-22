(ns ctia.lib.metrics.jmx
  (:require [clj-momo.lib.metrics.jmx :as jmx]
            [ctia.properties :refer [get-global-properties]]))

(defn init! []
  (when (get-in @(get-global-properties) [:ctia :metrics :jmx :enabled])
    (jmx/start)))
