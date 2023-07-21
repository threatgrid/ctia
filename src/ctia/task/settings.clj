(ns ctia.task.settings
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [ctia.stores.es.init :refer [init-es-conn! get-store-properties]]
            [ctia.stores.es.schemas :refer [ESConnServices]]
            [ctia.init :refer [log-properties]]
            [ctia.properties :as p]
            [ctia.store :refer [known-stores]]
            [schema.core :as s]))

(s/defn update-stores!
  [store-keys
   {{:keys [get-in-config]} :ConfigService
    :as services} :- ESConnServices]
  (doseq [kw store-keys]
    (log/infof "updating settings for store: %s" (name kw))
    (init-es-conn! (get-store-properties kw get-in-config)
                   services)))

(def cli-options
  [["-h" "--help"]
   ["-s" "--stores STORES" "comma separated list of store names"
    :default known-stores
    :parse-fn #(map keyword (str/split % #","))]])

(defn -main [& args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]
    (when errors
      (binding [*out* *err*]
        (println (str/join "\n" errors))
        (println summary))
      (System/exit 1))
    (when (:help options)
      (println summary)
      (System/exit 0))
    (pp/pprint options)
    (let [config (doto (p/build-init-config)
                   log-properties)
          get-in-config (partial get-in config)]
      (update-stores! (:stores options)
                      {:ConfigService {:get-in-config get-in-config}}))))
