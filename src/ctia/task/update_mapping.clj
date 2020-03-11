(ns ctia.task.update-mapping
  "Updates the _mapping on an ES index."
  (:require [clj-momo.lib.es.index :as es-index]
            [clj-momo.lib.es.schemas :refer [ESConnState]]
            [clojure.tools.logging :as log]
            [ctia.init :refer [init-store-service! log-properties]]
            [ctia.properties :refer [init!]]
            [ctia.store :refer [stores]]
            [schema.core :as s])
  (:import clojure.lang.ExceptionInfo))

(s/defn update-mapping-store
  "Updates _mapping on store"
  [{:keys [conn index] :as cs} :- ESConnState
   mappings :- s/Any]
  (es-index/update-mapping! conn index mappings))

(defn update-mapping-stores
  [stores]
  (reduce (fn [state [k store]]
            (log/infof "updating _mapping for store: %s" k)
            (assoc state k (mapv (fn [{:keys [state]}]
                                   (update-mapping-store state (get-in state [:config :mappings])))
                                 store)))
          {}
          stores))

(defn -main [& _args]
  (init!)
  (log-properties)
  (init-store-service!)
  (let [res (update-mapping-stores @stores)]
    (log/info "Completed update-mapping task: " res)))
