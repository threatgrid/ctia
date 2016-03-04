(ns cia.stores.sql.db
  (:require [cia.properties :as p]
            [korma.db :as kdb]))

(defonce db (atom nil))

(defn init! []
  (->> (kdb/create-db (p/prop :cia :store :sql :db))
       (reset! db)
       kdb/default-connection))
