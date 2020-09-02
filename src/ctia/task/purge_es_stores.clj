(ns ctia.task.purge-es-stores
  (:require [clojure.tools.logging :as log]
            [ctia
             [init :refer [start-ctia!*]]
             [properties :as p]]
            [ctia.stores.es.store :refer [delete-state-indexes]]
            [puppetlabs.trapperkeeper.app :as app]))

(defn setup
  "start CTIA store service.
  returns a tk app."
  []
  (log/info "starting CTIA Stores...")
  (let [config (p/build-init-config)]
    (start-ctia!* {:services [store-svc/store-service
                              es-svc/es-store-service]
                   :config config})))

(s/defn delete-store-indexes [stores :- {s/Keyword s/Any}]
  (doseq [store-impls (vals stores)
          {:keys [state]} store-impls]

    (log/infof "deleting index %s" (:index state))
    (delete-state-indexes state)))

(defn -main
  "invoke with lein run -m ctia.task.purge-es-stores"
  []
  (log/info "purging all ES Stores data")
  (try
    (let [app (setup)
          store-svc (app/get-service app :StoreService)
          deref-stores (partial store-svc/deref-stores store-svc)]
      (delete-store-indexes (deref-stores))
      (log/info "done")
      (System/exit 0))
    (finally
      (log/error "unknown error")
      (System/exit 1))))
