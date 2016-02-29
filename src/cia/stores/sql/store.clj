(ns cia.stores.sql.store
  (:require [cia.store :refer :all]
            [cia.stores.sql.judgement :as judgement]))

(defrecord JudgementStore []
  IJudgementStore
  (create-judgement [_ new-judgement]
    (first (judgement/insert-judgements new-judgement)))
  (read-judgement [_ id]
    (first (judgement/select-judgements {:id id})))
  (delete-judgement [_ id]
    (judgement/delete-judgement id))
  (list-judgements [_ filter-map]
    (judgement/select-judgements filter-map))
  (calculate-verdict [_ observable]
    (first (judgement/calculate-verdict observable))))
