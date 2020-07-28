(ns ctia.graphql-service
  (:require [ctia.graphql-service-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defprotocol GraphQLService
  (get-graphql [this] "Returns an instance of graphql.GraphQL")
  ;; Type registry to avoid any duplicates when using new-object
  ;; or new-enum. Contains a map with types indexed by name
  (get-or-update-type-registry
    [this name f]
    "If name exists in registry, return existing mapping. Otherwise conj
    {name (f)} to registry, and return (f)."))

(defn- GraphQLService-map [this]
  {:get-graphql (partial get-graphql this)
   :get-or-update-type-registry (partial get-or-update-type-registry this)})

(tk/defservice graphql-service
  GraphQLService
  [StoreService]
  (init [this context] (core/init context))
  (start [this context] (core/start context {:StoreService StoreService
                                             ;; FIXME is this a hack? perhaps expose an
                                             ;; init! method and delegate ServerService to calling it?
                                             :GraphQLService (GraphQLService-map this)}))
  (get-graphql [this] (core/get-graphql (service-context this)))
  (get-or-update-type-registry [this name f] (core/get-or-update-type-registry (:type-registry
                                                                                 (service-context this))
                                                                               name
                                                                               f)))
