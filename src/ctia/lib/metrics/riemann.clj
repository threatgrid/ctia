(ns ctia.lib.metrics.riemann
  (:require [clj-momo.lib.metrics.riemann :as riemann]
            [clojure.tools.logging :as log]
            [puppetlabs.trapperkeeper.core :as tk]))

(defn init! [get-in-config]
  (let [{enabled? :enabled :as config}
        (get-in-config [:ctia :metrics :riemann])]
    (when enabled?
      (log/info "riemann metrics reporting")
      (riemann/start (select-keys config
                                  [:host :port :interval-in-ms])))))

(defprotocol RiemannMetricsService)

(tk/defservice riemann-metrics-service
  [[:ConfigService get-in-config]]
  (start [this context]
         (init! get-in-config)
         context))
