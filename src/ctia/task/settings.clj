(ns ctia.task.settings
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]

            [schema.core :as s]
            [clj-momo.lib.es
             [index :as es-index]
             [schemas :refer [ESConnState]]]
            [ctia.stores.es.init :refer [upsert-template!]]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :refer [properties init!]]
             [store :refer [stores]]]))

(s/defn update-store!
  "read store properties of given stores and update indices settings."
  [{:keys [conn index config]
    {:keys [settings]} :config
    :as state} :- ESConnState]
  (try
    (upsert-template! conn index config)
    (log/info "updated template: " index)
    (->> {:index (select-keys settings [:refresh_interval :number_of_replicas])}
         (es-index/update-settings! conn index))
    (log/info "updated settings: " index)
    (catch clojure.lang.ExceptionInfo e
      (log/warn "could not update settings on that store"
                (pr-str (ex-data e))))))

(defn update-stores!
  [store-keys]
  (doseq [kw store-keys]
    (log/infof "updating settings for store: %s" (name kw))
    (update-store! (-> @stores kw first :state))))


(def cli-options
  [["-s" "--stores STORES" "comma separated list of store names"
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
    (init-store-service!)
    (update-stores! (:stores options))))
