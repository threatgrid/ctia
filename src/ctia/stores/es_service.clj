(ns ctia.stores.es-service
  (:require [ctia.stores.es-service-core :as core]
            [ctia.tk :as tk]))

(defprotocol ESStoreService)

(tk/defservice es-store-service
  ESStoreService
  [[:StoreService get-stores]]
  (start [this context]
         (core/start context
                     (get-stores))))
