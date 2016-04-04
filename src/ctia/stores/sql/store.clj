(ns ctia.stores.sql.store
  (:require [ctia.store :refer :all]
            [ctia.stores.sql.judgement :as judgement]))

(defrecord JudgementStore []
  IJudgementStore
  (create-judgement [_ login new-judgement]
    (first (judgement/insert-judgements login new-judgement)))
  (read-judgement [_ id]
    (first (judgement/select-judgements {:id id})))
  (delete-judgement [_ id]
    (judgement/delete-judgement id))
  (list-judgements [_ filter-map]
    (judgement/select-judgements filter-map))
  (calculate-verdict [_ observable]
    (judgement/calculate-verdict observable))
  (list-judgements-by-observable [this observable]
    (list-judgements this {[:observable :type]  (:type observable)
                           [:observable :value] (:value observable)}))
  (add-indicator-to-judgement [_ judgement-id indicator-rel]
    (if (judgement/create-judgement-indicator judgement-id indicator-rel)
      indicator-rel)))
