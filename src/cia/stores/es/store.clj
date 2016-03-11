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
                      IIncidentStore
                      IIdentityStore]]

   [cia.stores.es.judgement :as ju]
   [cia.stores.es.feedback  :as fe]
   [cia.stores.es.indicator :as in]
   [cia.stores.es.ttp :as ttp]
   [cia.stores.es.actor :as ac]
   [cia.stores.es.campaign :as ca]
   [cia.stores.es.coa :as coa]
   [cia.stores.es.incident :as inc]
   [cia.stores.es.exploit-target :as et]
   [cia.stores.es.identity :as id]))

(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgement [_ login new-judgement]
    (ju/handle-create-judgement state login new-judgement))
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
  (create-feedback [_ new-feedback login judgement-id]
    (fe/handle-create-feedback state new-feedback login judgement-id))
  (list-feedback [_ filter-map]
    (fe/handle-list-feedback state filter-map)))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicator [_ login new-indicator]
    (in/handle-create-indicator state login new-indicator))
  (update-indicator [_ id login new-indicator]
    (in/handle-update-indicator state login id new-indicator))
  (read-indicator [_ id]
    (in/handle-read-indicator state id))
  (delete-indicator [_ id]
    (in/handle-delete-indicator state id))
  (list-indicators [_ filter-map]
    (in/handle-list-indicators state filter-map))
  ;; (list-indicators-by-observable [_ judgement-store observable]
  ;;   (in/handle-list-indicators-by-observable state
  ;;                                            judgement-store
  ;;                                            observable))
  ;; (list-indicator-sightings-by-observable [_ judgement-store observable]
  ;;   (->> (in/handle-list-indicators-by-observable state
  ;;                                                 judgement-store
  ;;                                                 observable)
  ;;        (mapcat :sightings)))
  )

(defrecord TTPStore [state]
  ITTPStore
  (read-ttp [_ id]
    (ttp/handle-read-ttp state id))
  (create-ttp [_ login new-ttp]
    (ttp/handle-create-ttp state login new-ttp))
  (update-ttp [_ id login new-ttp]
    (ttp/handle-update-ttp state login id new-ttp))
  (delete-ttp [_ id]
    (ttp/handle-delete-ttp state id))
  (list-ttps [_ filter-map]
    (ttp/handle-list-ttps state filter-map)))

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id]
    (ac/handle-read-actor state id))
  (create-actor [_ login new-actor]
    (ac/handle-create-actor state login new-actor))
  (update-actor [_ id login actor]
    (ac/handle-update-actor state login id actor))
  (delete-actor [_ id]
    (ac/handle-delete-actor state id))
  (list-actors [_ filter-map]
    (ac/handle-list-actors state filter-map)))

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id]
    (ca/handle-read-campaign state id))
  (create-campaign [_ login new-campaign]
    (ca/handle-create-campaign state login new-campaign))
  (update-campaign [_ id login new-campaign]
    (ca/handle-update-campaign state login id new-campaign))
  (delete-campaign [_ id]
    (ca/handle-delete-campaign state id))
  (list-campaigns [_ filter-map]
    (ca/handle-list-campaigns state filter-map)))

(defrecord COAStore [state]
  ICOAStore
  (read-coa [_ id]
    (coa/handle-read-coa state id))
  (create-coa [_ login new-coa]
    (coa/handle-create-coa state login new-coa))
  (update-coa [_ id login new-coa]
    (coa/handle-update-coa state login id new-coa))
  (delete-coa [_ id]
    (coa/handle-delete-coa state id))
  (list-coas [_ filter-map]
    (coa/handle-list-coas state filter-map)))

(defrecord IncidentStore [state]
  IIncidentStore
  (read-incident [_ id]
    (inc/handle-read-incident state id))
  (create-incident [_ login new-incident]
    (inc/handle-create-incident state login new-incident))
  (update-incident [_ id login new-incident]
    (inc/handle-update-incident state login id new-incident))
  (delete-incident [_ id]
    (inc/handle-delete-incident state id))
  (list-incidents [_ filter-map]
    (inc/handle-list-incidents state filter-map)))

(defrecord ExploitTargetStore [state]
  IExploitTargetStore
  (read-exploit-target [_ id]
    (et/handle-read-exploit-target state id))
  (create-exploit-target [_ login new-exploit-target]
    (et/handle-create-exploit-target state login new-exploit-target))
  (update-exploit-target [_ id login new-exploit-target]
    (et/handle-update-exploit-target state login id new-exploit-target))
  (delete-exploit-target [_ id]
    (et/handle-delete-exploit-target state id))
  (list-exploit-targets [_ filter-map]
    (et/handle-list-exploit-targets state filter-map)))

(defrecord IdentityStore [state]
  IIdentityStore
  (read-identity [_ login]
    (id/handle-read-identity state login))
  (create-identity [_ new-identity]
    (id/handle-create-identity state new-identity))
  (delete-identity [_ org-id role]
    (id/handle-delete-identity state org-id role)))
