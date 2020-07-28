(ns ctia.graphql-service
  (:require [ctia.graphql-service-core :as core]
            [ctia.store-service :as store-svc]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defprotocol GraphQLService
  (get-graphql [this] "Returns an instance of graphql.GraphQL")
  (get-or-update-type-registry
    [this name f]
    "If name exists in registry, return existing mapping. Otherwise conj
    {name (f)} to registry, and return (f)."))

(tk/defservice graphql-service
  GraphQLService
  [StoreService]
  (start [_ context] (core/start context {:StoreService (store-svc/lift-store-service-fns
                                                          StoreService)}))
  (get-graphql [this] (core/get-graphql (service-context this)))
  (get-or-update-type-registry [this name f] (core/get-or-update-type-registry (:type-registry
                                                                                 (service-context this))
                                                                               name
                                                                               f)))
