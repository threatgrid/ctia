(ns cia.stores.es.store
  (:require
   [cia.store :refer :all]
   [cia.stores.es.judgement :refer :all]
   [cia.stores.es.feedback  :refer :all]
   [cia.stores.es.indicator :refer :all]
   [cia.stores.es.ttp :refer :all]
   [cia.stores.es.actor :refer :all]
   [cia.stores.es.campaign :refer :all]
   [cia.stores.es.coa :refer :all]
   [cia.stores.es.incident :refer :all]
   [cia.stores.es.exploit-target :refer :all]))


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

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id]
    (handle-read-campaign state id))
  (create-campaign [_ new-campaign]
    (handle-create-campaign state new-campaign))
  (update-campaign [_ id new-campaign]
    (handle-update-campaign state id new-campaign))
  (delete-campaign [_ id]
    (handle-delete-campaign state id))
  (list-campaigns [_ filter-map]))

(defrecord COAStore [state]
  ICOAStore
  (read-coa [_ id]
    (handle-read-coa state id))
  (create-coa [_ new-coa]
    (handle-create-coa state new-coa))
  (update-coa [_ id new-coa]
    (handle-update-coa state id new-coa))
  (delete-coa [_ id]
    (handle-delete-coa state id))
  (list-coas [_ filter-map]))

(defrecord IncidentStore [state]
  IIncidentStore
  (read-incident [_ id]
    (handle-read-incident state id))
  (create-incident [_ new-incident]
    (handle-create-incident state new-incident))
  (update-incident [_ id new-incident]
    (handle-update-incident state id new-incident))
  (delete-incident [_ id]
    (handle-delete-incident state id))
  (list-incidents [_ filter-map]))

;; TBD rename
(defrecord ExplitTargetStore [state]
  IExploitTargetStore
  (read-exploit-target [_ id]
    (handle-read-exploit-target state id))
  (create-exploit-target [_ new-exploit-target]
    (handle-create-exploit-target state new-exploit-target))
  (update-exploit-target [_ id new-exploit-target]
    (handle-update-exploit-target state id new-exploit-target))
  (delete-exploit-target [_ id]
    (handle-delete-exploit-target state id))
  (list-exploit-targets [_ filter-map]))
