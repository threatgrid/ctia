(ns ctia.lib.metrics.console
  (:require [clj-momo.lib.metrics.console :as console]
            [ctia.properties :refer [properties]]))

(defn init! []
  (let [{:keys [enabled interval]}
        (get-in @properties [:ctia :metrics :console])]
    (when enabled
      (console/start interval))))
