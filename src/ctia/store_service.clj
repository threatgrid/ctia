(ns ctia.store-service
  (:require [ctia.store-service-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]
            [schema.core :as s]))

(defprotocol StoreService
  (all-stores [this] "Returns a map of current stores.

                     See also: ctia.store-service.schemas/AllStoresFn")
  (write-store [this store-id write-fn]
               [this store-id write-fn opt]
               "Updates store at store-id using write-fn.

               Options:
               - :args  write-fn will be filled in as #(apply write-fn % (:args opt))

               See also: ctia.store-service.schemas/WriteStoreFn")
  (read-store [this store-id read-fn]
              "Returns the result of passing store with store-id to read-fn.

              See also: ctia.store-service.schemas/ReadStoreFn"))

(tk/defservice store-service
  "A service to manage the central storage area for all stores."
  StoreService
  [[:ConfigService get-in-config]]
  (init [this context] (core/init context))
  (start [this context]
         (core/start context
                     get-in-config))
  (stop [this context]
        (core/stop context))

  (all-stores [this] (core/all-stores (service-context this)))
  (write-store [this store write-fn] (write-store this store write-fn {}))
  (write-store [this store write-fn opt]
               (core/write-store (service-context this)
                                 store write-fn
                                 opt))
  (read-store [this store read-fn]
              (core/read-store (service-context this)
                               store
                               read-fn)))
