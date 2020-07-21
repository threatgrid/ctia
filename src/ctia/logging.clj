(ns ctia.logging
  (:require [ctia.logging-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]))

(tk/defservice event-logging-service
  [[:EventsService register-listener]]
  (start [this context] (core/start context register-listener))
  (stop [this context] (core/stop context)))
