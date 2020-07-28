(ns ctia.graphql-service
  (:require [ctia.graphql-service-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defprotocol GraphQLService
  (get-graphql [this] "Returns an instance of graphql.GraphQL")
  ;; Type registry to avoid any duplicates when using new-object
  ;; or new-enum. Contains a map with types indexed by name
  (get-type-registry [this] "Returns the type registry, an atom"))

(defn- GraphQLService-map [this]
  {:get-graphql #(get-graphql this)
   :get-type-registry #(get-type-registry this)})

(tk/defservice graphql-service
  GraphQLService
  [StoreService]
  (start [this context] (core/start context {:StoreService StoreService
                                             :GraphQLService (GraphQLService-map this)}))
  (get-graphql [this] (core/get-graphql (service-context this)))
  (get-type-registry [this] (core/get-type-registry (service-context this))))
