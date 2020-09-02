(ns ctia.lib.metrics.console
  (:require [clj-momo.lib.metrics.console :as console]
            [ctia.properties :as p]))

(defn init! [get-in-config]
  (let [{:keys [enabled interval]}
        (get-in-config [:ctia :metrics :console])]
    (when enabled
      (console/start interval))))
