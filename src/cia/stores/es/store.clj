(ns cia.stores.es.store
  (:require
   [cia.store :refer :all]
   [cia.stores.es.judgement :refer :all]))


(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgement [_ new-judgement]
    (handle-create-judgement state new-judgement))
  (read-judgement [_ id]
    (handle-read-judgement state id))
  (delete-judgement [_ id]
    (handle-delete-judgement state id))
  (list-judgements [_ filter-map]
    (handle-list-judgements state filter-map))
  (calculate-verdict [_ observable]
    (handle-calculate-verdict state observable)))
