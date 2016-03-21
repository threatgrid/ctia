(ns ctia.test-helpers.db
  (:require [ctia.store :as store]
            [ctia.stores.sql.db :as sql-db]
            [ctia.stores.sql.judgement :as sql-judgement]
            [ctia.stores.sql.store :as sql-store]
            [ctia.test-helpers.core :as h]
            [clojure.java.io :as io]
            [korma.core :as k]))

(def ddl (atom nil))
(def tables (atom nil))

(defn read-ddl []
  (or @ddl
      (reset! ddl
              (slurp
               (io/reader
                (io/as-file
                 (io/resource "schema.sql")))))))

(defn table-names []
  (or @tables
      (reset! tables
              (map last (re-seq #"CREATE TABLE (\w+)"
                                (read-ddl))))))

(defn create-tables [db]
  (k/exec-raw db (read-ddl)))

(defn exec-raw-for-all-tables [f]
  (fn [db]
    (doseq [t (table-names)]
      (k/exec-raw db (f t)))))

(def truncate-tables
  (exec-raw-for-all-tables
   (fn [table]
     (format "TRUNCATE TABLE \"%s\";" table))))

(def drop-tables
  (exec-raw-for-all-tables
   (fn [table]
     (format "DROP TABLE IF EXISTS \"%s\";" table))))

(defn fixture-init-db [test]
  (sql-db/init!)
  (drop-tables @sql-db/db)
  (create-tables @sql-db/db)
  (sql-judgement/init!)
  (test)
  (drop-tables @sql-db/db))

(defn fixture-clean-db [f]
  (truncate-tables @sql-db/db)
  (f))

(def sql-stores
  {store/judgement-store sql-store/->JudgementStore})

(def fixture-sql-store (h/fixture-store (merge h/memory-stores
                                               sql-stores)))
