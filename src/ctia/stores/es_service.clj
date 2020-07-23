(ns ctia.stores.es-service
  (:require [ctia.stores.es-service-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]))

(tk/defservice es-store-service
  [[:StoreService get-stores]]
  (start [this context]
         (core/start context
                     (get-stores))))
