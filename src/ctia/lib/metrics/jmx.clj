(ns ctia.lib.metrics.jmx
  (:require [clj-momo.lib.metrics.jmx :as jmx]
            [ctia.properties :refer [properties]]))

(defn init! []
  (when (get-in @properties [:ctia :metrics :jmx :enabled])
    (jmx/start)))
