(ns ctia.stores.es.store
  (:require [clj-momo.lib.es.index :as es-index]
            [schema.core :as s]))

(defn delete-state-indexes [{:keys [conn index config]}]
  (when conn
    (es-index/delete! conn (str index "*"))))





