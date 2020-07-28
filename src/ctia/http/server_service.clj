(ns ctia.http.server-service
  (:require [ctia.http.server-service-core :as core]
            [ctia.properties :as p]
            [ctia.store-service :as store-svc]
            [puppetlabs.trapperkeeper.core :as tk]))

(defprotocol CTIAHTTPServerService)

(tk/defservice ctia-http-server-service
  CTIAHTTPServerService
  [HooksService
   StoreService
   IAuth
   GraphQLService
   IEncryption]
  (start [this context] (core/start context
                                    (get-in (p/read-global-properties) [:ctia :http])
                                    {:HooksService (-> HooksService 
                                                       (select-keys [:apply-hooks
                                                                     :apply-event-hooks]))
                                     :StoreService (-> StoreService 
                                                       (select-keys [:read-store
                                                                     :write-store])
                                                       store-svc/lift-store-service-fns)
                                     :IAuth IAuth
                                     :GraphQLService GraphQLService
                                     :IEncryption IEncryption}))
  (stop [this context] (core/stop context)))
