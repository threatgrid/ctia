(ns ctia.lib.metrics.riemann
  (:require [clj-momo.lib.metrics.riemann :as riemann]
            [ctia.properties :as p]
            [clojure.tools.logging :as log]))

(defn init! []
  (let [{enabled? :enabled :as config}
        (p/get-in-global-properties [:ctia :metrics :riemann])]
    (when enabled?
      (log/info "riemann metrics reporting")
      (riemann/start (select-keys config
                                  [:host :port :interval-in-ms])))))
