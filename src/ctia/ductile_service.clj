(ns ctia.ductile-service
  (:require [ctia.ductile-service.core :as core]
            [puppetlabs.trapperkeeper.core :as tk]))

(defprotocol DuctileService
  (request-fn [this req] "Ignoring `this`, implements the same interface as the 1-argument
                         arity of clj-http.client/request."))

(tk/defservice ductile-service
  "A service to configure interactions with ductile."
  DuctileService
  []
  (request-fn [_ req] (core/request-fn req)))
