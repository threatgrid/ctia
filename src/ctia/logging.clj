(ns ctia.logging
  (:require [ctia.logging-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]))

(defprotocol EventLoggingService)

(defn ->event-logging-service [{:keys [log-fn]}]
  (tk/service EventLoggingService
    [[:EventsService register-listener]]
    (start [this context] (core/start context register-listener log-fn))
    (stop [this context] (core/stop context))))

(def event-logging-service (->event-logging-service {}))
