(ns ctia.graphql-service
  (:require [ctia.graphql-service-core :as core]
            [ctia.store-service :as store-svc]
            [ctia.tk :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defprotocol GraphQLService
  (get-graphql [this] "Returns an instance of graphql.GraphQL"))

(tk/defservice graphql-service
  GraphQLService
  [ConfigService
   StoreService]
  (start [_ context] (core/start context {:ConfigService (-> ConfigService
                                                             (select-keys [:get-in-config]))
                                          :StoreService (-> StoreService
                                                            (select-keys [:read-store])
                                                            store-svc/lift-store-service-fns)}))
  (get-graphql [this] (core/get-graphql (service-context this))))
