(ns ctia.lib.metrics.jmx
  (:require [metrics.reporters.jmx :as jmx]
            [ctia.properties :refer [properties]]))

(defn init! []
  (when (get-in @properties [:ctia :metrics :jmx :enabled])
    (jmx/start (jmx/reporter {}))))
