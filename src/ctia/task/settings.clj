(ns ctia.task.settings
  (:import clojure.lang.ExceptionInfo)
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [clj-momo.lib.es
             [index :as es-index]
             [schemas :refer [ESConnState]]]
            [ctia.stores.es.init :refer [init-es-conn! get-store-properties]]
            [ctia
             [init :refer [log-properties]]
             [properties :refer [init!]]
             [store :refer [get-global-stores]]
             [store-service-core :refer [empty-stores]]]))

(defn update-stores!
  [store-keys]
  (doseq [kw store-keys]
    (log/infof "updating settings for store: %s" (name kw))
    (init-es-conn! (get-store-properties kw))))

(def cli-options
  [["-h" "--help"]
   ["-s" "--stores STORES" "comma separated list of store names"
    :default (set (keys empty-stores))
    :parse-fn #(map keyword (str/split % #","))]])

(defn -main [& args]
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
    (init!)
    (log-properties)
    (update-stores! (:stores options))))
