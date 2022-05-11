(ns ctia.task.rollover
  (:require [clojure.tools.logging :as log]
            [ctia.init :refer [start-ctia!*]]
            [ctia.store-service :as store-svc]
            [ctia.features-service :as features-svc]
            [ctia.stores.es.schemas :refer [ESConnState]]
            [ctia.properties :as p]
            [puppetlabs.trapperkeeper.app :as app]
            ductile.index
            [schema.core :as s])
  (:import clojure.lang.ExceptionInfo))

(s/defn rollover-store
  "Sends rollover query on a store if paramater aliased and rollover conditions are configured."
  [{conn :conn
    {:keys [write-index aliased]
     conditions :rollover} :props} :- ESConnState]
  (when (and aliased (seq conditions))
    (let [{rolledover? :rolled_over :as response}
          (ductile.index/rollover! conn write-index conditions)]
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


(defn mk-app! []
  (let [config (p/build-init-config)]
    (start-ctia!* {:services [store-svc/store-service
                              features-svc/features-service]
                   :config config})))

(defn rollover-stores
  [stores]
  (reduce concat-rollover
          {:nb-errors 0}
          stores))

(defn -main [& _args]
  (try
    (let [app (mk-app!)
          {{:keys [all-stores]} :StoreService} (app/service-graph app)
          {:keys [nb-errors]
           :as res} (rollover-stores (all-stores))]
      (log/info "completed rollover task: " res)
      (if (< 0 nb-errors)
        (do
          (log/error "there were errors while rolling over stores")
          (System/exit 1))
        (System/exit 0)))
    (catch Throwable e
      (log/error e "Unknown error")
      (System/exit 2))))
