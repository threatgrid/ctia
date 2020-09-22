(ns ctia.lib.metrics.jmx
  (:require [clj-momo.lib.metrics.jmx :as jmx]
            [puppetlabs.trapperkeeper.core :as tk]))

(defn init! [get-in-config]
  (when (get-in-config [:ctia :metrics :jmx :enabled])
    (jmx/start)))

(defprotocol JMXMetricsService)

(tk/defservice jmx-metrics-service
  JMXMetricsService
  [[:ConfigService get-in-config]]
  (start [this context]
         (init! get-in-config)
         context))
