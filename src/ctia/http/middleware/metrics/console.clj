(ns ctia.http.middleware.metrics.console
  (:require [metrics.reporters.console :as console]
            [ctia.properties :refer [properties]]))

(defn init! []
  (let [{:keys [enabled interval]}
        (get-in @properties [:ctia :metrics :console])]
    (when enabled
      (console/start (console/reporter {}) interval))))
