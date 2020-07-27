(ns ctia.http.server-service
  (:require [ctia.http.server-service-core :as core]
            [ctia.properties :as p]
            [ctia.store-service :refer [store-service-fn->varargs]]
            [puppetlabs.trapperkeeper.core :as tk]))

(defprotocol CTIAHTTPServerService)

(tk/defservice ctia-http-server-service
  CTIAHTTPServerService
  [HooksService
   StoreService]
  (start [this context] (core/start context
                                    (get-in (p/read-global-properties) [:ctia :http])
                                    {:HooksService (select-keys HooksService [:apply-hooks
                                                                              :apply-event-hooks])
                                     :StoreService (-> StoreService 
                                                       (select-keys [:read-store :write-store])
                                                       (update :read-store store-service-fn->varargs)
                                                       (update :write-store store-service-fn->varargs))}))
  (stop [this context] (core/stop context)))
