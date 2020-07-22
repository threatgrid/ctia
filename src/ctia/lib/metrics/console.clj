(ns ctia.lib.metrics.console
  (:require [clj-momo.lib.metrics.console :as console]
            [ctia.properties :refer [get-global-properties]]))

(defn init! []
  (let [{:keys [enabled interval]}
        (get-in @(get-global-properties) [:ctia :metrics :console])]
    (when enabled
      (console/start interval))))
