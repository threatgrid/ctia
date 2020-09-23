(ns ctia.task.rollover
  (:require [clj-momo.lib.es.index :as es-index]
            [clojure.tools.logging :as log]
            [ctia.init :refer [start-ctia!*]]
            [ctia.store-service :as store-svc]
            [ctia.stores.es.schemas :refer [ESConnState]]
            [ctia.properties :as p]
            [puppetlabs.trapperkeeper.app :as app]
            [schema.core :as s])
  (:import clojure.lang.ExceptionInfo))

(s/defn rollover-store
  "Sends rollover query on a store if paramater aliased and rollover conditions are configured."
  [{conn :conn
    {:keys [write-index aliased]
     conditions :rollover} :props} :- ESConnState]
  (when (and aliased (seq conditions))
    (let [{rolledover? :rolled_over :as response}
          (es-index/rollover! conn write-index conditions)]
      (when rolledover?
        (log/info "rolled over: " (pr-str response)))
      response)))

(defn concat-rollover
  [state [k store]]
  (log/infof "requesting _rollover for store: %s" k)
  (try
    (->> (first store)
         :state
         (rollover-store)
         (assoc state k))
    (catch ExceptionInfo e
      (log/error (format "could not rollover %s, a concurrent rollover could be already running on that index %s"
                         k
                         (pr-str (ex-data e))))
      (update state :nb-errors inc))))

(defn rollover-stores
  [stores]
  (reduce concat-rollover
          {:nb-errors 0}
          stores))

(defn -main [& _args]
  (try
    (let [app (let [config (p/build-init-config)]
                (start-ctia!* {:services [store-svc/store-service]
                               :config config}))
          {{:keys [all-stores]} :StoreService} (app/service-graph app)
          {:keys [nb-errors]
           :as res} (rollover-stores (all-stores))]
      (log/info "completed rollover task: " res)
      (if (< 0 nb-errors)
        (do
          (log/error "there were errors while rolling over stores")
          (System/exit 1))
        (System/exit 0)))
    (finally
      (log/error "Unknown error")
      (System/exit 2))))
