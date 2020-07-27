(ns ctia.store-service
  (:require [ctia.store-service-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defonce global-store-service (atom nil))

(defprotocol StoreService
  (get-stores [this] "Returns the atom of stores")
  (write-store [this store write-fn])
  (read-store [this store read-fn]))

(tk/defservice store-service
  "A service to manage an atom that is the central
  storage area for all stores."
  StoreService
  []
  (init [this context]
        (reset! global-store-service this)
        (core/init context))
  ;; this impl can be deleted with global-store-service
  (stop [this context]
        (reset! global-store-service nil)
        context)

  (get-stores [this] (core/get-stores (service-context this)))
  (write-store [this store write-fn]
               (core/write-store (service-context this)
                                 store write-fn))
  (read-store [this store read-fn]
              (core/read-store (service-context this)
                               store read-fn)))

(defn store-service-fn->varargs
  "Given a 2-argument write-store or read-store function (eg., from defservice),
  lifts the function to support variable arguments."
  [store-svc-fn]
  {:pre [store-svc-fn]}
  (fn [store f & args]
    (store-svc-fn store #(apply f % args))))
