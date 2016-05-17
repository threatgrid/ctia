(ns ctia.stores.atom.store
  (:require [ctia.store :refer :all]
            [ctia.stores.atom
             [actor :as actor]
             [campaign :as campaign]
             [coa :as coa]
             [exploit-target :as expl-tar]
             [feedback :as feedback]
             [identity :as identity]
             [incident :as incident]
             [indicator :as indicator]
             [judgement :as judgement]
             [sighting :as sighting]
             [ttp :as ttp]]))

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id]
    (actor/handle-read-actor state id))
  (create-actor [_ new-actor]
    (actor/handle-create-actor state new-actor))
  (update-actor [_ id actor]
    (actor/handle-update-actor state id actor))
  (delete-actor [_ id]
    (actor/handle-delete-actor state id))
  (list-actors [_ filter-map params]))

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id]
    (campaign/handle-read-campaign state id))
  (create-campaign [_ new-campaign]
    (campaign/handle-create-campaign state new-campaign))
  (update-campaign [_ id new-campaign]
    (campaign/handle-update-campaign state id new-campaign))
  (delete-campaign [_ id]
    (campaign/handle-delete-campaign state id))
  (list-campaigns [_ filter-map params]))

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
  (list-coas [_ filter-map params]))

(defrecord ExploitTargetStore [state]
  IExploitTargetStore
  (read-exploit-target [_ id]
    (expl-tar/handle-read-exploit-target state id))
  (create-exploit-target [_ new-exploit-target]
    (expl-tar/handle-create-exploit-target state new-exploit-target))
  (update-exploit-target [_ id new-exploit-target]
    (expl-tar/handle-update-exploit-target state id new-exploit-target))
  (delete-exploit-target [_ id]
    (expl-tar/handle-delete-exploit-target state id))
  (list-exploit-targets [_ filter-map params]))

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback]
    (feedback/handle-create-feedback state new-feedback))
  (read-feedback [_ id]
    (feedback/handle-read-feedback state id))
  (list-feedback [_ filter-map params]
    (feedback/handle-list-feedback state filter-map params))
  (delete-feedback [_ id]
    (feedback/handle-delete-feedback state id)))

(defrecord IdentityStore [state]
  IIdentityStore
  (read-identity [_ login]
    (identity/handle-read-identity state login))
  (create-identity [_ new-identity]
    (identity/handle-create-identity state new-identity))
  (delete-identity [_ org-id role]
    (identity/handle-delete-identity state org-id role)))

(defrecord IncidentStore [state]
  IIncidentStore
  (read-incident [_ id]
    (incident/handle-read-incident state id))
  (create-incident [_ new-incident]
    (incident/handle-create-incident state new-incident))
  (update-incident [_ id incident]
    (incident/handle-update-incident state id incident))
  (delete-incident [_ id]
    (incident/handle-delete-incident state id))
  (list-incidents [_ filter-map params]))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicator [_ new-indicator]
    (indicator/handle-create-indicator state new-indicator))
  (update-indicator [_ id new-indicator]
    (indicator/handle-update-indicator state id new-indicator))
  (read-indicator [_ id]
    (indicator/handle-read-indicator state id))
  (delete-indicator [_ id]
    (indicator/handle-delete-indicator state id))
  (list-indicators [_ filter-map params]
    (indicator/handle-list-indicators state filter-map params))
  (list-indicators-by-judgements [_ judgements params]
    (indicator/handle-list-indicators-by-judgements state judgements params)))

(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgement [_ new-judgement]
    (judgement/handle-create-judgement state new-judgement))
  (read-judgement [_ id]
    (judgement/handle-read-judgement state id))
  (delete-judgement [_ id]
    (judgement/handle-delete-judgement state id))
  (list-judgements [_ filter-map params]
    (judgement/handle-list-judgements state filter-map params))
  (calculate-verdict [_ observable]
    (judgement/handle-calculate-verdict state observable))
  (list-judgements-by-observable [this observable params]
    (list-judgements this {[:observable :type]  (:type observable)
                           [:observable :value] (:value observable)} params))
  (add-indicator-to-judgement [_ judgement-id indicator-rel]
    (judgement/handle-add-indicator-to-judgement state
                                                 judgement-id
                                                 indicator-rel)))

(defrecord SightingStore [state]
  ISightingStore
  (read-sighting [_ id]
    (sighting/handle-read-sighting state id))
  (create-sighting [_ new-sighting]
    (sighting/handle-create-sighting state new-sighting))
  (update-sighting [_ id sighting]
    (sighting/handle-update-sighting state id sighting))
  (delete-sighting [_ id]
    (sighting/handle-delete-sighting state id))
  (list-sightings [_ filter-map params]
    (sighting/handle-list-sightings state filter-map params))
  (list-sightings-by-indicators [_ indicators params]
    (sighting/handle-list-sightings-by-indicators state indicators params))
  (list-sightings-by-observables [_ observables params]
    (sighting/handle-list-sightings-by-observables state observables params)))

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
  (list-ttps [_ filter-map params]))
