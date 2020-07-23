(ns ctia.task.purge-es-stores
  (:require [clojure.tools.logging :as log]
            [ctia
             [init :refer [log-properties]]
             [properties :as p]]
            [ctia.stores.es.store :refer [delete-state-indexes]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.core :as tk]))

(defn setup
  "start CTIA store service.
  returns a tk app."
  []
  (log/info "starting CTIA Stores...")
  (p/init!)
  (log-properties)
  (tk/boot-services-with-config
    [store-svc/store-service
     es-svc/es-store-service]
    (p/read-global-properties)))

(defn delete-store-indexes [stores]
  (doseq [store-impls (vals @stores)
          {:keys [state]} store-impls]

    (log/infof "deleting index %s" (:index state))
    (delete-state-indexes state)))

(defn -main
  "invoke with lein run -m ctia.task.purge-es-stores"
  []
  (log/info "purging all ES Stores data")
  (try
    (let [app (setup)
          store-svc (app/get-service app :StoreService)]
      (delete-store-indexes (store-svc/get-stores
                              store-svc))
      (log/info "done")
      (System/exit 0))
    (finally
      (log/error "unknown error")
      (System/exit 1))))
