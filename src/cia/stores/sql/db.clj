(ns cia.stores.sql.db
  (:require [clojure.java.io :as io]
            [korma.db :as kdb])
  (:import java.util.Properties))

(def db-properties-file "sql-database.properties")

(defonce db (atom nil))

(defn read-db-spec []
  (let [props (Properties.)]
    (.load props (-> db-properties-file
                     io/resource
                     io/reader))
    (into {} (map (fn [[k v]]
                    [(keyword k) v])
                  props))))



(defn init! []
  (->> (kdb/create-db (read-db-spec))
       (reset! db)
       kdb/default-connection))
