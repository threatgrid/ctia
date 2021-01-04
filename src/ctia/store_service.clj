(ns ctia.store-service
  (:require [ctia.store-service-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]
            [schema.core :as s]))

(defprotocol StoreService
  (all-stores [this] "Returns a map of current stores.

                     See also: ctia.store-service.schemas/AllStoresFn")
  (get-store [this store-id]
              "Returns the identified store.

              See also: ctia.store-service.schemas/GetStoreFn"))

(tk/defservice store-service
  "A service to manage the central storage area for all stores."
  StoreService
  [[:ConfigService get-in-config]]
  (init [this context]
        (core/init context))
  (start [this context]
         (core/start context
                     get-in-config))
  (stop [this context]
        (core/stop context))

  (all-stores [this]
              (core/all-stores (service-context this)))
  (get-store [this store-id]
              (core/get-store (service-context this)
                               store-id)))
