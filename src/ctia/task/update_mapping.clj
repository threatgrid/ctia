(ns ctia.task.update-mapping
  "Updates the _mapping on an ES index."
  (:require [clj-momo.lib.es.index :as es-index]
            [clj-momo.lib.es.schemas :as es-schema]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ctia.init :as init]
            [ctia.properties :as properties]
            [ctia.store :as store]
            [ctia.stores.es.init :as es-init]
            [schema.core :as s])
  (:import [clojure.lang ExceptionInfo]))

(defn- update-mapping-state!
  [{:keys [conn index config props] :as state}]
  (let [write-index (:write-index props)
        indices (cond-> #{index}
                  (and (:aliased props) write-index) (conj write-index))]
    (doto conn
      ; template update should go first in the (unlikely) case of
      ; a race condition with a simuntaneously successful rollover.
      #_
      (es-init/upsert-template!
        index
        config)
      (es-index/update-mapping!
        (str/join "," indices)
        (:mappings config)))
    nil))

(defn update-mapping-stores!
  "Takes a map the same shape as @ctia.store/stores
  and updates the _mapping of every index contained in it."
  [stores-map]
  (doseq [[_ stores] stores-map
          {:keys [state]} stores]
    (update-mapping-state! state)))

(defn -main [& _args]
  (properties/init!)
  (init/log-properties)
  (init/init-store-service!)
  (update-mapping-stores! @store/stores)
  (log/info "Completed update-mapping task"))
