(ns ctia.http.server-service
  (:require [ctia.http.server-service-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defprotocol CTIAHTTPServerService
  (get-graphql [this] "Returns an instance of graphql.GraphQL")
  (get-port [this] "Returns the port bound by HTTP server"))

(tk/defservice ctia-http-server-service
  CTIAHTTPServerService
  [HooksService
   StoreService
   IAuth
   GraphQLNamedTypeRegistryService
   IEncryption
   ConfigService
   FeaturesService
   RiemannService]
  (start [this context] (core/start context
                                    ((:get-in-config ConfigService) [:ctia :http])
                                    {:ConfigService                   (-> ConfigService
                                                                          (select-keys #{:get-config
                                                                                         :get-in-config}))
                                     :HooksService                    (-> HooksService
                                                                          (select-keys #{:apply-event-hooks
                                                                                         :apply-hooks}))
                                     :StoreService                    (-> StoreService
                                                                          (select-keys #{:get-store}))
                                     :IAuth                           IAuth
                                     :GraphQLNamedTypeRegistryService GraphQLNamedTypeRegistryService
                                     :IEncryption                     IEncryption
                                     :FeaturesService                 FeaturesService
                                     :RiemannService                  (-> RiemannService
                                                                          (select-keys #{:send-event}))}))
  (stop [this context] (core/stop context))
  (get-port [this]
            (core/get-port (service-context this)))
  (get-graphql [this]
               (core/get-graphql (service-context this))))
