(ns ctia.stores.sql.store
  (:require [ctia.store :refer :all]
            [ctia.stores.sql.judgement :as judgement]))

(defrecord JudgementStore []
  IJudgementStore
  (create-judgement [_ new-judgement]
    (first (judgement/insert-judgements new-judgement)))
  (read-judgement [_ id]
    (-> (judgement/select-judgements {:id id} nil)
        :data
        first))
  (delete-judgement [_ id]
    (judgement/delete-judgement id))
  (list-judgements [_ filter-map params]
    (judgement/select-judgements filter-map params))
  (calculate-verdict [_ observable]
    (judgement/calculate-verdict observable))
  (list-judgements-by-observable [this observable params]
    (list-judgements this {[:observable :type]  (:type observable)
                           [:observable :value] (:value observable)} params))
  (add-indicator-to-judgement [_ judgement-id indicator-rel]
    (if (judgement/create-judgement-indicator judgement-id indicator-rel)
      indicator-rel)))
