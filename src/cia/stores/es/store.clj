(ns cia.stores.es.store
  (:require
   [cia.store :refer :all]
   [cia.stores.es.judgement :refer :all]
   [cia.stores.es.feedback  :refer :all]
   [cia.stores.es.indicator :refer :all]
   [cia.stores.es.ttp :refer :all]
   [cia.stores.es.actor :refer :all]
   ))


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

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback judgement-id]
    (handle-create-feedback state new-feedback judgement-id))
  (list-feedback [_ filter-map]
    (handle-list-feedback state filter-map)))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicator [_ new-indicator]
    (handle-create-indicator state new-indicator))
  (update-indicator [_ id new-indicator]
    (handle-update-indicator state id new-indicator))
  (read-indicator [_ id]
    (handle-read-indicator state id))
  (delete-indicator [_ id]
    (handle-delete-indicator state id))
  (list-indicators [_ filter-map]
    (handle-list-indicators state filter-map))
  (list-indicators-by-observable [_ judgement-store observable]
    (handle-list-indicators-by-observable state
                                          judgement-store
                                          observable))
  (list-indicator-sightings-by-observable [_ judgement-store observable]
    (->> (handle-list-indicators-by-observable state
                                               judgement-store
                                               observable)
         (mapcat :sightings))))

(defrecord TTPStore [state]
  ITTPStore
  (read-ttp [_ id]
    (handle-read-ttp state id))
  (create-ttp [_ new-ttp]
    (handle-create-ttp state new-ttp))
  (update-ttp [_ id new-ttp]
    (handle-update-ttp state id new-ttp))
  (delete-ttp [_ id]
    (handle-delete-ttp state id))
  (list-ttps [_ filter-map]))

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id]
    (handle-read-actor state id))
  (create-actor [_ new-actor]
    (handle-create-actor state new-actor))
  (update-actor [_ id actor]
    (handle-update-actor state id actor))
  (delete-actor [_ id]
    (handle-delete-actor state id))
  (list-actors [_ filter-map]))
