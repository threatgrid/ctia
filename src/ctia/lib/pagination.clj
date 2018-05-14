(ns ctia.lib.pagination
  (:require [schema.core :as s]))

(def default-limit 100)

(defn list-response-schema
  "generate a list response schema for a model"
  [Model]
  {:data [Model]
   :paging {s/Any s/Any}})

(defn paginate
  [data {:keys [sort_by sort_order offset limit]
         :or {sort_by :id
              sort_order :asc
              offset 0
              limit default-limit}}]
  (as-> data $
    (sort-by sort_by $)
    (if (= :desc sort_order)
      (reverse $) $)
    (drop offset $)
    (take limit $)))
