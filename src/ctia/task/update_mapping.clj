(ns ctia.task.update-mapping
  "Updates the _mapping on an ES index."
  (:require [clj-momo.lib.es.index :as es-index]
            [clj-momo.lib.es.schemas :as es-schema]
            [clojure.tools.logging :as log]
            [ctia.init :as init]
            [ctia.properties :as properties]
            [ctia.store :as store]
            [schema.core :as s])
  (:import [clojure.lang ExceptionInfo]))

(s/defn update-mapping-store
  "Updates _mapping on store"
  [{:keys [conn index] :as cs} :- es-schema/ESConnState
   mappings :- s/Any]
  (es-index/update-mapping! conn index mappings))

(defn update-mapping-stores
  [stores-map]
  (into {}
        (map (fn [[k stores]]
               (log/infof "updating _mapping for store: %s" k)
               [k (mapv (fn [{:keys [state]}]
                          (update-mapping-store state (get-in state [:config :mappings])))
                        stores)]))
        stores-map))

(defn -main [& _args]
  (properties/init!)
  (init/log-properties)
  (init/init-store-service!)
  (let [res (update-mapping-stores @store/stores)]
    (log/info "Completed update-mapping task: " res)))
