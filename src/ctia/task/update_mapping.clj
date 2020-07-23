(ns ctia.task.update-mapping
  "Updates the _mapping on an ES index."
  (:require [clj-momo.lib.es.index :as es-index]
            [clj-momo.lib.es.schemas :as es-schema]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [ctia.init :as init]
            [ctia.properties :as p]
            [ctia.store :as store]
            [ctia.store-service :as store-svc]
            [ctia.store-service-core :refer [empty-stores]]
            [ctia.stores.es-service :as es-svc]
            [ctia.stores.es.init :as es-init]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.core :as tk]
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
  "Takes a map the same shape as @(ctia.store/get-global-stores)
  and updates the _mapping of every index contained in it."
  [stores-map]
  (doseq [[_ stores] stores-map
          {:keys [state]} stores]
    (update-mapping-state! state)))

(def cli-options
  [["-h" "--help"]
   ["-s" "--stores STORES" "comma separated list of store names"
    :default (set (keys empty-stores))
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
      (p/init!)
      (init/log-properties)
      (let [app (tk/boot-services-with-config
                  [store-svc/store-service
                   es-svc/es-store-service]
                  @(p/get-global-properties))
            store-svc (app/get-service app :StoreService)]
        (->> (:stores options)
             (select-keys @(store-svc/get-stores
                             store-svc))
             update-mapping-stores!)
        (log/info "Completed update-mapping task")
        (System/exit 0)))
    (finally
      (log/error "unknown error")
      (System/exit 1))))
