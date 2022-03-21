(ns ctia.lib.riemann-service
  (:require [ctia.lib.riemann :as core]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defprotocol RiemannService
  (send-event [this event] [this service-prefix event]))

(tk/defservice riemann-service
  RiemannService
  [[:ConfigService get-in-config]]
  (start [this context] (core/start context (get-in-config [:ctia :log :riemann])))
  (stop [this context] (core/stop context))
  (send-event [this event] (let [{:keys [conn service-prefix]} (service-context this)]
                             (core/send-event conn service-prefix event)))
  (send-event [this service-prefix event] (let [{:keys [conn]} (service-context this)]
                                            (core/send-event conn service-prefix event))))
