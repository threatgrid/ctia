(ns ctia.logging
  (:require [ctia.logging-core :as core]
            [ctia.tk :as tk]))

(defprotocol EventLoggingService)

(tk/defservice event-logging-service
  EventLoggingService
  [[:EventsService register-listener]]
  (start [this context] (core/start context register-listener))
  (stop [this context] (core/stop context)))
