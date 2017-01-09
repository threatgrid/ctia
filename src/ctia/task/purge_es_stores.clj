(ns ctia.task.purge-es-stores
  (:require [clojure.tools.logging :as log]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :as p]
             [store :refer [stores]]]
            [ctia.stores.es.store :refer [delete-state-indexes]]))

(defn setup
  "start CTIA store service"
  []
  (log/info "starting CTIA Stores...")
  (p/init!)
  (log-properties)
  (init-store-service!))

(defn delete-store-indexes []
  (doseq [store-impls (vals @stores)
          {:keys [state]} store-impls]

    (log/infof "deleting index %s" (:index state))
    (delete-state-indexes state)))

(defn -main
  "invoke with lein run -m ctia.task.purge-es-stores"
  []
  (log/info "purging all ES Stores data")
  (setup)
  (delete-store-indexes)
  (println "done")
  (System/exit 0))
