(ns cia.stores.es.store
  (:require
   [cia.store :refer [IActorStore
                      IJudgementStore
                      IIndicatorStore
                      IExploitTargetStore
                      IFeedbackStore
                      ITTPStore
                      ICampaignStore
                      ICOAStore
                      ISightingStore
                      IIncidentStore]]

   [cia.stores.es.judgement :as ju]
   [cia.stores.es.feedback  :as fe]
   [cia.stores.es.indicator :as in]
   [cia.stores.es.ttp :as ttp]
   [cia.stores.es.actor :as ac]
   [cia.stores.es.campaign :as ca]
   [cia.stores.es.coa :as coa]
   [cia.stores.es.incident :as inc]
   [cia.stores.es.exploit-target :as et]))

(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgement [_ new-judgement]
    (ju/handle-create-judgement state new-judgement))
  (read-judgement [_ id]
    (ju/handle-read-judgement state id))
  (delete-judgement [_ id]
    (ju/handle-delete-judgement state id))
  (list-judgements [_ filter-map]
    (ju/handle-list-judgements state filter-map))
  (calculate-verdict [_ observable]
    (ju/handle-calculate-verdict state observable)))

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback judgement-id]
    (fe/handle-create-feedback state new-feedback judgement-id))
  (list-feedback [_ filter-map]
    (fe/handle-list-feedback state filter-map)))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicator [_ new-indicator]
    (in/handle-create-indicator state new-indicator))
  (update-indicator [_ id new-indicator]
    (in/handle-update-indicator state id new-indicator))
  (read-indicator [_ id]
    (in/handle-read-indicator state id))
  (delete-indicator [_ id]
    (in/handle-delete-indicator state id))
  (list-indicators [_ filter-map]
    (in/handle-list-indicators state filter-map))
  (list-indicators-by-observable [_ judgement-store observable]
    (in/handle-list-indicators-by-observable state
                                             judgement-store
                                             observable))
  (list-indicator-sightings-by-observable [_ judgement-store observable]
    (->> (in/handle-list-indicators-by-observable state
                                                  judgement-store
                                                  observable)
         (mapcat :sightings))))

(defrecord TTPStore [state]
  ITTPStore
  (read-ttp [_ id]
    (ttp/handle-read-ttp state id))
  (create-ttp [_ new-ttp]
    (ttp/handle-create-ttp state new-ttp))
  (update-ttp [_ id new-ttp]
    (ttp/handle-update-ttp state id new-ttp))
  (delete-ttp [_ id]
    (ttp/handle-delete-ttp state id))
  (list-ttps [_ filter-map]
    (ttp/handle-list-ttps state filter-map)))

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id]
    (ac/handle-read-actor state id))
  (create-actor [_ new-actor]
    (ac/handle-create-actor state new-actor))
  (update-actor [_ id actor]
    (ac/handle-update-actor state id actor))
  (delete-actor [_ id]
    (ac/handle-delete-actor state id))
  (list-actors [_ filter-map]
    (ac/handle-list-actors state filter-map)))

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id]
    (ca/handle-read-campaign state id))
  (create-campaign [_ new-campaign]
    (ca/handle-create-campaign state new-campaign))
  (update-campaign [_ id new-campaign]
    (ca/handle-update-campaign state id new-campaign))
  (delete-campaign [_ id]
    (ca/handle-delete-campaign state id))
  (list-campaigns [_ filter-map]
    (ca/handle-list-campaigns state filter-map)))

(defrecord COAStore [state]
  ICOAStore
  (read-coa [_ id]
    (coa/handle-read-coa state id))
  (create-coa [_ new-coa]
    (coa/handle-create-coa state new-coa))
  (update-coa [_ id new-coa]
    (coa/handle-update-coa state id new-coa))
  (delete-coa [_ id]
    (coa/handle-delete-coa state id))
  (list-coas [_ filter-map]
    (coa/handle-list-coas state filter-map)))

(defrecord IncidentStore [state]
  IIncidentStore
  (read-incident [_ id]
    (inc/handle-read-incident state id))
  (create-incident [_ new-incident]
    (inc/handle-create-incident state new-incident))
  (update-incident [_ id new-incident]
    (inc/handle-update-incident state id new-incident))
  (delete-incident [_ id]
    (inc/handle-delete-incident state id))
  (list-incidents [_ filter-map]
    (inc/handle-list-incidents state filter-map)))

(defrecord ExploitTargetStore [state]
  IExploitTargetStore
  (read-exploit-target [_ id]
    (et/handle-read-exploit-target state id))
  (create-exploit-target [_ new-exploit-target]
    (et/handle-create-exploit-target state new-exploit-target))
  (update-exploit-target [_ id new-exploit-target]
    (et/handle-update-exploit-target state id new-exploit-target))
  (delete-exploit-target [_ id]
    (et/handle-delete-exploit-target state id))
  (list-exploit-targets [_ filter-map]
    (et/handle-list-exploit-targets state filter-map)))
