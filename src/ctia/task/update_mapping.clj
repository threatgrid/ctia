(ns ctia.task.update-mapping
  "Updates the _mapping on an ES index."
  (:require [clj-momo.lib.es.index :as es-index]
            [clj-momo.lib.es.schemas :as es-schema]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ctia.init :as init]
            [ctia.properties :as p]
            [ctia.store :as store]
            [ctia.store-service :as store-svc]
            [ctia.stores.es.init :as es-init]
            [puppetlabs.trapperkeeper.app :as app]
            [schema.core :as s])
  (:import [clojure.lang ExceptionInfo]))

(defn- update-mapping-state!
  [{:keys [conn index config] :as state}]
  (run! #(% conn index config)
        ; template update should go first in the (unlikely) case of
        ; a race condition with a simultaneously successful rollover.
        [es-init/upsert-template!
         es-init/update-mapping!]))

(defn update-mapping-stores!
  "Takes a map the same shape as returned by ctia.store-service/all-stores
  and updates the _mapping of every index contained in it."
  [stores-map]
  (doseq [[_ stores] stores-map
          {:keys [state]} stores]
    (update-mapping-state! state)))

(def cli-options
  [["-h" "--help"]
   ["-s" "--stores STORES" "comma separated list of store names"
    :default (set (keys store/empty-stores))
    :parse-fn #(map keyword (str/split % #","))]])

(defn -main [& args]
  (try
    (let [{:keys [options errors summary]} (parse-opts args cli-options)]
      (when errors
        (binding  [*out* *err*]
          (println (str/join "\n" errors))
          (println summary))
        (System/exit 1))
      (when (:help options)
        (println summary)
        (System/exit 0))
      (pp/pprint options)
      (let [app (init/start-ctia!* {:services [store-svc/store-service]
                                    :config (p/build-init-config)})
            {{:keys [all-stores]} :StoreService} (app/service-graph app)]
        (->> (:stores options)
             (select-keys (all-stores))
             update-mapping-stores!)
        (log/info "Completed update-mapping task")
        (System/exit 0)))
    (catch Throwable e
      (log/error e "Error!")
      (System/exit 1))))
