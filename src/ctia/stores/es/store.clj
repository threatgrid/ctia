(ns ctia.stores.es.store
  (:require [ctia.lib.es.index :as es-index :refer [connect ESConnState]]
            [schema.core :as s]
            [ctia.stores.es
             [actor :as ac]
             [campaign :as ca]
             [coa :as coa]
             [exploit-target :as et]
             [feedback :as fe]
             [identity :as id]
             [incident :as inc]
             [indicator :as in]
             [judgement :as ju]
             [verdict :as ve]
             [mapping :refer [store-mappings]]
             [sighting :as sig]
             [ttp :as ttp]]
            [ctia.store :refer [IActorStore
                                ICampaignStore
                                ICOAStore
                                IExploitTargetStore
                                IFeedbackStore
                                IIdentityStore
                                IIncidentStore
                                IIndicatorStore
                                IJudgementStore
                                IVerdictStore
                                ISightingStore
                                ITTPStore]]))

(s/defn init-store-conn :- ESConnState
  "initiate an ES store connection returns a map containing transport,
   mapping, and the configured index name"
  [{:keys [indexname] :as props}]

  {:index indexname
   :props props
   :mapping store-mappings
   :conn (connect props)})

(s/defn init! :- ESConnState
  "initiate an ES Store connection,
  create the index if needed, returns the es conn state"
  [props]
  (let [{:keys [conn index mapping] :as conn-state} (init-store-conn props)]
    (es-index/create! conn index mapping)
    conn-state))

(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgement [_ new-judgement]
    (ju/handle-create-judgement state new-judgement))
  (add-indicator-to-judgement [_ judgement-id indicator-rel]
    (ju/handle-add-indicator-to-judgement state judgement-id indicator-rel))
  (read-judgement [_ id]
    (ju/handle-read-judgement state id))
  (delete-judgement [_ id]
    (ju/handle-delete-judgement state id))
  (list-judgements [_ filter-map params]
    (ju/handle-list-judgements state filter-map params))
  (list-judgements-by-observable [this observable params]
    (ju/handle-list-judgements state {[:observable :type]  (:type observable)
                                      [:observable :value] (:value observable)} params))
  (calculate-verdict [_ observable]
    (ju/handle-calculate-verdict state observable)))

(defrecord VerdictStore [state]
  IVerdictStore
  (create-verdict [_ new-verdict]
    (ve/handle-create-verdict state new-verdict))
  (read-verdict [_ id]
    (ve/handle-read-verdict state id))
  (delete-verdict [_ id]
    (ve/handle-delete-verdict state id))
  (list-verdicts [_ filter-map params]
    (ve/handle-list-verdicts state filter-map params)))

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback]
    (fe/handle-create-feedback state new-feedback))
  (read-feedback [_ id]
    (fe/handle-read-feedback state id))
  (delete-feedback [_ id]
    (fe/handle-delete-feedback state id))
  (list-feedback [_ filter-map params]
    (fe/handle-list-feedback state filter-map params)))

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
  (list-indicators [_ filter-map params]
    (in/handle-list-indicators state filter-map params))
  (list-indicators-by-judgements [_ judgements params]
    (in/handle-list-indicators-by-judgements state judgements params)))

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
  (list-ttps [_ filter-map params]
    (ttp/handle-list-ttps state filter-map params)))

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
  (list-actors [_ filter-map params]
    (ac/handle-list-actors state filter-map params)))

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
  (list-campaigns [_ filter-map params]
    (ca/handle-list-campaigns state filter-map params)))

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
  (list-coas [_ filter-map params]
    (coa/handle-list-coas state filter-map params)))

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
  (list-incidents [_ filter-map params]
    (inc/handle-list-incidents state filter-map params)))

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
  (list-exploit-targets [_ filter-map params]
    (et/handle-list-exploit-targets state filter-map params)))

(defrecord IdentityStore [state]
  IIdentityStore
  (read-identity [_ login]
    (id/handle-read-identity state login))
  (create-identity [_ new-identity]
    (id/handle-create-identity state new-identity))
  (delete-identity [_ org-id role]
    (id/handle-delete-identity state org-id role)))

(defrecord SightingStore [state]
  ISightingStore
  (read-sighting [_ id]
    (sig/handle-read-sighting state id))
  (create-sighting [_ new-sighting]
    (sig/handle-create-sighting state new-sighting))
  (update-sighting [_ id sighting]
    (sig/handle-update-sighting state id sighting))
  (delete-sighting [_ id]
    (sig/handle-delete-sighting state id))
  (list-sightings [_ filter-map params]
    (sig/handle-list-sightings state filter-map params))
  (list-sightings-by-observables [_ observables params]
    (sig/handle-list-sightings-by-observables state observables params)))
