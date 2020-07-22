(ns ctia.http.server-service
  (:require [ctia.http.server-service-core :as core]
            [ctia.properties :as p]
            [puppetlabs.trapperkeeper.core :as tk]))

(defprotocol CTIAHTTPServerService)

(tk/defservice ctia-http-server-service
  CTIAHTTPServerService
  []
  (start [this context] (core/start context
                                    (get-in @(p/get-global-properties) [:ctia :http])))
  (stop [this context] (core/stop context)))
