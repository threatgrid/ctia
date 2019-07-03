(ns ctia.task.rollover
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]

            [schema.core :as s]
            [clj-momo.lib.es
             [index :as es-index]
             [schemas :refer [ESConnState]]]

            [ctia.stores.es.crud :as es-crud]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :refer [properties init!]]
             [store :refer [stores]]]))

(s/defn rollover-store
  "Sends rollover query on a store if paramater aliased and rollover conditions are configured."
  [{conn :conn
    {:keys [write-index aliased]
     conditions :rollover} :props} :- ESConnState]
  (when (and aliased (seq conditions))
    (try
      (let [{rolledover? :rolled_over :as response}
            (es-index/rollover! conn write-index conditions)]
        (when rolledover?
          (log/info "rolled over: " (pr-str response)))
        response)
      (catch clojure.lang.ExceptionInfo e
        (log/warn "could not rollover, a concurrent rollover could be already running on that index"
                  (pr-str (ex-data e)))))))

(defn rollover-stores
  [stores]
  (doseq [[k store] stores]
    (log/infof "requesting _rollover for store: %s" k)
    (rollover-store (-> store first :state))))

(defn -main [& args]
  (init!)
  (log-properties)
  (init-store-service!)
  (rollover-stores @stores))
