(ns ctia.task.rollover
  (:require [clj-momo.lib.es.index :as es-index]
            [clj-momo.lib.es.schemas :refer [ESConnState]]
            [clojure.tools.logging :as log]
            [ctia.init :refer [log-properties]]
            [ctia.store-service :as store-svc]
            [ctia.stores.es-service :as es-svc]
            [ctia.properties :as p]
            [ctia.store :refer [get-global-stores]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.core :as tk]
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
      (log/error (format "could not rollover, a concurrent rollover could be already running on that index"
                         k
                         (pr-str (ex-data e))))
      (update state :nb-errors inc))))

(defn rollover-stores
  [stores]
  (reduce concat-rollover
          {:nb-errors 0}
          stores))

(defn -main [& _args]
  (p/init!)
  (log-properties)
  (try
    (let [app (tk/boot-services-with-config
                [store-svc/store-service
                 es-svc/es-store-service]
                (p/read-global-properties))
          store-svc (app/get-service app :StoreService)
          {:keys [nb-errors]
           :as res} (rollover-stores @(store-svc/get-stores store-svc))]
      (log/info "completed rollover task: " res)
      (if (< 0 nb-errors)
        (do
          (log/error "there were errors while rolling over stores")
          (System/exit 1))
        (System/exit 0)))
    (finally
      (log/error "Unknown error")
      (System/exit 2))))
