(ns ctia.task.settings
  (:import clojure.lang.ExceptionInfo)
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [clj-momo.lib.es
             [index :as es-index]
             [schemas :refer [ESConnState]]]
            [ctia.stores.es.init :refer [StoreProperties init-es-conn! get-store-properties]]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :refer [properties init!]]
             [store :refer [stores]]]))

(s/defn update-store!
  "read store properties of given stores and update indices settings."
  [props :- StoreProperties]
  (try
    (let [{:keys [conn index]
           {:keys [settings]} :config} (init-es-conn! props)]
      (->> {:index (select-keys settings [:refresh_interval :number_of_replicas])}
           (es-index/update-settings! conn index))
      (log/info "updated settings:" index))
    (catch ExceptionInfo e
      (log/warn "could not update settings on that store"
                (pr-str (ex-data e))))))

(defn update-stores!
  [store-keys]
  (doseq [kw store-keys]
    (log/infof "updating settings for store: %s" (name kw))
    (update-store! (get-store-properties kw))))

(def cli-options
  [["-h" "--help"]
   ["-s" "--stores STORES" "comma separated list of store names"
    :default (set (keys @stores))
    :parse-fn #(map keyword (clojure.string/split % #","))]])

(defn -main [& args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]
    (when errors
      (binding  [*out* *err*]
        (println (clojure.string/join "\n" errors))
        (println summary))
      (System/exit 1))
    (when (:help options)
      (println summary)
      (System/exit 0))
    (clojure.pprint/pprint options)
    (init!)
    (log-properties)
    (update-stores! (:stores options))))
