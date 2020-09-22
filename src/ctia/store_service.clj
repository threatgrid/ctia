(ns ctia.store-service
  (:require [ctia.store-service-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]
            [schema.core :as s]))

(defprotocol StoreService
  (all-stores [this] "Returns a map of current stores")
  (write-store [this store write-fn])
  (read-store [this store read-fn]))

(tk/defservice store-service
  "A service to manage an atom that is the central
  storage area for all stores."
  StoreService
  [[:ConfigService get-in-config]]
  (init [this context] (core/init context))
  (start [this context]
         (core/start context
                     get-in-config))

  (all-stores [this] (core/all-stores (service-context this)))
  (write-store [this store write-fn]
               (core/write-store (service-context this)
                                 store write-fn))
  (read-store [this store read-fn]
              (core/read-store (service-context this)
                               store read-fn)))

(s/defn store-service-fn->varargs
  "Given a 2-argument write-store or read-store function (eg., from defservice),
  lifts the function to support variable arguments."
  [store-svc-fn :- (s/=> s/Any
                         (s/named s/Any 
                                  'store)
                         (s/named (s/=> s/Any s/Any)
                                  'f))]
  {:pre [store-svc-fn]}
  (fn [store f & args]
    (store-svc-fn store #(apply f % args))))

(defn lift-store-service-fns
  "Given a map of StoreService services (via defservice), lift
  them to support variable arguments."
  [services]
  (cond-> services
    (:read-store services) (update :read-store store-service-fn->varargs)
    (:write-store services) (update :write-store store-service-fn->varargs)))
