(ns ctia.ductile-service
  (:require [ctia.ductile-service.core :as core]
            [puppetlabs.trapperkeeper.core :as tk]))

(defprotocol DuctileService
  (request-fn [this req]))

(tk/defservice ductile-service
  "A service to configure interactions with ductile."
  DuctileService
  (request-fn [_ req] (core/request-fn req)))
