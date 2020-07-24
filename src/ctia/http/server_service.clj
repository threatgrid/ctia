(ns ctia.http.server-service
  (:require [ctia.http.server-service-core :as core]
            [ctia.properties :as p]
            [puppetlabs.trapperkeeper.core :as tk]))

(defprotocol CTIAHTTPServerService)

(tk/defservice ctia-http-server-service
  CTIAHTTPServerService
  [[:HooksService apply-hooks apply-event-hooks]]
  (start [this context] (core/start context
                                    (get-in (p/read-global-properties) [:ctia :http])
                                    apply-hooks
                                    apply-event-hooks))
  (stop [this context] (core/stop context)))
