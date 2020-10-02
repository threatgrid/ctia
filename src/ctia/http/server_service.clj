(ns ctia.http.server-service
  (:require [ctia.http.server-service-core :as core]
            [ctia.properties :as p]
            [ctia.flows.hooks-service :as hooks-svc]
            [ctia.store-service :as store-svc]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defprotocol CTIAHTTPServerService
  (get-port [this]))

(tk/defservice ctia-http-server-service
  CTIAHTTPServerService
  [HooksService
   StoreService
   IAuth
   GraphQLService
   GraphQLNamedTypeRegistryService
   IEncryption
   ConfigService]
  (start [_ context] (core/start context
                                 ((:get-in-config ConfigService) [:ctia :http])
                                 {:ConfigService (-> ConfigService
                                                     (select-keys [:get-config
                                                                   :get-in-config]))
                                  :HooksService (-> HooksService 
                                                    (select-keys [:apply-hooks
                                                                  :apply-event-hooks])
                                                    hooks-svc/lift-hooks-service-fns)
                                  :StoreService (-> StoreService 
                                                    (select-keys [:read-store
                                                                  :write-store])
                                                    store-svc/lift-store-service-fns)
                                  :IAuth IAuth
                                  :GraphQLService GraphQLService
                                  :GraphQLNamedTypeRegistryService GraphQLNamedTypeRegistryService
                                  :IEncryption IEncryption}))
  (stop [_ context] (core/stop context))

  (get-port [this] (core/get-port (service-context this))))
