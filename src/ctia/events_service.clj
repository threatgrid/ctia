(ns ctia.events-service
  (:require [ctia.events-service-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defonce global-events-service (atom nil))

(defprotocol IEventsService
  (central-channel [this] "Returns the central channel")
  (send-event [this event] [this chan event]
              "Send an event to a channel. Use the central channel by default")
  (register-listener [this listen-fn mode]
                     [this pred listen-fn mode]
                     "Convenience wrapper for registering a listener on the central event channel."))

(tk/defservice events-service
  IEventsService
  []
  (init [this context] (core/init context))
  (stop [this context] (core/stop context))
  
  (central-channel [this] (core/central-channel (service-context this)))
  (send-event [this event] (core/send-event (service-context this) event))
  (send-event [this chan event] (core/send-event (service-context this) chan event))
  (register-listener
    [this listen-fn mode] (core/register-listener (service-context this)
                                                  listen-fn mode))
  (register-listener
    [this pred listen-fn mode] (core/register-listener (service-context this)
                                                       pred listen-fn mode)))
