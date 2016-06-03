(ns ctia.stores.sql.db
  (:require [ctia.properties :refer [properties]]
            [korma.db :as kdb]))

(defonce db (atom nil))

(defn init! [props]
  (->> (kdb/create-db props)
       (reset! db)
       kdb/default-connection))

(defn uninitialized? []
  (nil? @db))

(defn shutdown! []
  (reset! db nil))
