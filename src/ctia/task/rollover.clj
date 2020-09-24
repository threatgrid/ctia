(ns ctia.task.rollover
  (:require [clj-momo.lib.es.index :as es-index]
            [clojure.tools.logging :as log]
            [ctia.init :refer [init-store-service! log-properties]]
            [ctia.properties :as p]
            [ctia.store :refer [stores]]
            [ctia.stores.es.schemas :refer [ESConnState]]
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
  (let [config (doto (p/build-init-config)
                 log-properties)
        _ (init-store-service! (partial get-in config))
        {:keys [nb-errors]
         :as res} (rollover-stores @stores)]
    (log/info "completed rollover task: " res)
    (when (< 0 nb-errors)
      (log/error "there were errors while rolling over stores")
      (System/exit 1))))
