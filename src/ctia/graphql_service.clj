(ns ctia.graphql-service
  (:require [ctia.graphql-service-core :as core]
            [ctia.store-service :as store-svc]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defprotocol GraphQLService
  (get-graphql [this] "Returns an instance of graphql.GraphQL"))

(tk/defservice graphql-service
  GraphQLService
  [ConfigService
   CTIAHTTPServerPortService
   StoreService
   GraphQLNamedTypeRegistryService
   IEncryption]
  (start [_ context] (core/start context {:ConfigService (-> ConfigService
                                                             (select-keys [:get-in-config]))
                                          :CTIAHTTPServerPortService (-> CTIAHTTPServerPortService
                                                                         (select-keys [:get-port]))
                                          :StoreService (-> StoreService
                                                            (select-keys [:read-store])
                                                            store-svc/lift-store-service-fns)
                                          :GraphQLNamedTypeRegistryService
                                          (-> GraphQLNamedTypeRegistryService
                                              (select-keys [:get-or-update-named-type-registry]))
                                          :IEncryption
                                          (-> IEncryption
                                              (select-keys [:decrypt :encrypt]))}))
  (get-graphql [this] (core/get-graphql (service-context this))))
