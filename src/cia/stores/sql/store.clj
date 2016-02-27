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

;; (defrecord IndicatorStore [state]
;;   IIndicatorStore
;;   (create-indicator [_ new-indicator]
;;     (handle-create-indicator state new-indicator))
;;   (read-indicator [_ id]
;;     (handle-read-indicator state id))g
;;   (delete-indicator [_ id]
;;     (handle-delete-indicator state id))
;;   (list-indicators [_ filter-map]
;;     (handle-list-indicators state filter-map))
;;   (list-indicator-sightings [_ filter-map]
;;     (handle-list-indicators-sightings state filter-map)))
