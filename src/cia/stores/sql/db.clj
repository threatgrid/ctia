(ns cia.stores.sql.db
  (:require [cia.properties :refer [properties]]
            [korma.db :as kdb]))

(defonce db (atom nil))

(defn init! []
  (->> (kdb/create-db (get-in @properties [:cia :store :sql :db]))
       (reset! db)
       kdb/default-connection))
