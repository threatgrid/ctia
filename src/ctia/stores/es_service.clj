(ns ctia.stores.es-service
  (:require [ctia.stores.es-service-core :as core]
            [ctia.tk :as tk]))

(defprotocol ESStoreService)

(tk/defservice es-store-service
  ESStoreService
  [[:ConfigService get-in-config]
   [:StoreService get-stores]]
  (start [this context]
         (core/start context
                     (get-stores)
                     get-in-config)))
