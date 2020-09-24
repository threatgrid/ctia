(ns ctia.task.purge-es-stores
  (:require [clojure.tools.logging :as log]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :as p]
             [store :as store]]
            [ctia.stores.es.store :refer [delete-state-indexes]]))

(defn setup
  "start CTIA store service"
  []
  (log/info "starting CTIA Stores...")
  (let [config (doto (p/build-init-config)
                 log-properties)]
    (init-store-service! (partial get-in config))))

(defn delete-store-indexes [all-stores]
  (doseq [:let [stores (all-stores)
                _ (assert (map? stores))]
          store-impls (vals stores)
          {:keys [state]} store-impls]

    (log/infof "deleting index %s" (:index state))
    (delete-state-indexes state)))

(defn -main
  "invoke with lein run -m ctia.task.purge-es-stores"
  []
  (log/info "purging all ES Stores data")
  (setup)
  (delete-store-indexes (fn _all-stores []
                          @store/stores))
  (log/info "done")
  (System/exit 0))
