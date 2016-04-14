(ns ctia.stores.sql.common
  (:require [korma.core :as k]))

(defn insert
  "Performs a korma insert but with a guard around empty rows"
  [entity rows]
  (when-not (empty? rows)
    (k/insert entity (k/values rows))))
