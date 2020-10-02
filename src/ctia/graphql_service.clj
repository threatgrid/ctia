(ns ctia.graphql-service
  (:require [ctia.graphql-service-core :as core]
            [ctia.http.server-service :as server-svc]
            [ctia.store-service :as store-svc]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :as svc]))

(defprotocol GraphQLService
  (get-graphql [this] "Returns an instance of graphql.GraphQL"))

(tk/defservice graphql-service
  GraphQLService
  [ConfigService
   StoreService
   GraphQLNamedTypeRegistryService
   IEncryption]
  (start [this context] (core/start context
                                    ;; tie the knot between CTIAHTTPServerService and GraphQLService
                                    #(server-svc/get-port (svc/get-service this :CTIAHTTPServerService))
                                    {:ConfigService (-> ConfigService
                                                        (select-keys [:get-in-config]))
                                     :StoreService (-> StoreService
                                                       (select-keys [:read-store])
                                                       store-svc/lift-store-service-fns)
                                     :GraphQLNamedTypeRegistryService
                                     (-> GraphQLNamedTypeRegistryService
                                         (select-keys [:get-or-update-named-type-registry]))
                                     :IEncryption
                                     (-> IEncryption
                                         (select-keys [:decrypt :encrypt]))}))
  (get-graphql [this] (core/get-graphql (svc/service-context this))))
