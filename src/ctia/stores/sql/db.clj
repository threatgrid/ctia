(ns ctia.stores.sql.db
  (:require [ctia.properties :refer [properties]]
            [korma.db :as kdb]))

(defonce db (atom nil))

(defn init! []
  (->> (kdb/create-db (get-in @properties [:ctia :store :sql :db]))
       (reset! db)
       kdb/default-connection))

(defn uninitialized? []
  (nil? @db))

(defn shutdown! []
  (reset! db nil))
