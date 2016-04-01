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
  (create-actor [_ login new-actor]
    (actor/handle-create-actor state login new-actor))
  (update-actor [_ id login actor]
    (actor/handle-update-actor state id login actor))
  (delete-actor [_ id]
    (actor/handle-delete-actor state id))
  (list-actors [_ filter-map]))

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id]
    (campaign/handle-read-campaign state id))
  (create-campaign [_ login new-campaign]
    (campaign/handle-create-campaign state login new-campaign))
  (update-campaign [_ id login new-campaign]
    (campaign/handle-update-campaign state id login new-campaign))
  (delete-campaign [_ id]
    (campaign/handle-delete-campaign state id))
  (list-campaigns [_ filter-map]))

(defrecord COAStore [state]
  ICOAStore
  (read-coa [_ id]
    (coa/handle-read-coa state id))
  (create-coa [_ login new-coa]
    (coa/handle-create-coa state login new-coa))
  (update-coa [_ id login new-coa]
    (coa/handle-update-coa state id login new-coa))
  (delete-coa [_ id]
    (coa/handle-delete-coa state id))
  (list-coas [_ filter-map]))

(defrecord ExploitTargetStore [state]
  IExploitTargetStore
  (read-exploit-target [_ id]
    (expl-tar/handle-read-exploit-target state id))
  (create-exploit-target [_ login new-exploit-target]
    (expl-tar/handle-create-exploit-target state login new-exploit-target))
  (update-exploit-target [_ id login new-exploit-target]
    (expl-tar/handle-update-exploit-target state id login new-exploit-target))
  (delete-exploit-target [_ id]
    (expl-tar/handle-delete-exploit-target state id))
  (list-exploit-targets [_ filter-map]))

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback login judgement-id]
    (feedback/handle-create-feedback state new-feedback login judgement-id))
  (list-feedback [_ filter-map]
    (feedback/handle-list-feedback state filter-map)))

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
  (create-incident [_ login new-incident]
    (incident/handle-create-incident state login new-incident))
  (update-incident [_ id login incident]
    (incident/handle-update-incident state id login incident))
  (delete-incident [_ id]
    (incident/handle-delete-incident state id))
  (list-incidents [_ filter-map]))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicator [_ login new-indicator]
    (indicator/handle-create-indicator state login new-indicator))
  (update-indicator [_ id login new-indicator]
    (indicator/handle-update-indicator state id login new-indicator))
  (read-indicator [_ id]
    (indicator/handle-read-indicator state id))
  (delete-indicator [_ id]
    (indicator/handle-delete-indicator state id))
  (list-indicators [_ filter-map]
    (indicator/handle-list-indicators state filter-map))
  (list-indicators-by-judgements [_ judgements]
    (indicator/handle-list-indicators-by-judgements state judgements)))

(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgement [_ login new-judgement]
    (judgement/handle-create-judgement state login new-judgement))
  (read-judgement [_ id]
    (judgement/handle-read-judgement state id))
  (delete-judgement [_ id]
    (judgement/handle-delete-judgement state id))
  (list-judgements [_ filter-map]
    (judgement/handle-list-judgements state filter-map))
  (calculate-verdict [_ observable]
    (judgement/handle-calculate-verdict state observable))
  (list-judgements-by-observable [this observable]
    (list-judgements this {[:observable :type]  (:type observable)
                           [:observable :value] (:value observable)}))
  (add-indicator-to-judgement [_ judgement-id indicator-rel]
    (judgement/handle-add-indicator-to-judgement state
                                                  judgement-id
                                                  indicator-rel)))

(defrecord SightingStore [state]
  ISightingStore
  (read-sighting [_ id]
    (sighting/handle-read-sighting state id))
  (create-sighting [_ login new-sighting]
    (sighting/handle-create-sighting state login new-sighting))
  (update-sighting [_ id login sighting]
    (sighting/handle-update-sighting state id login sighting))
  (delete-sighting [_ id]
    (sighting/handle-delete-sighting state id))
  (list-sightings [_ filter-map]
    (sighting/handle-list-sightings state filter-map))
  (list-sightings-by-indicators [_ indicators]
    (sighting/handle-list-sightings-by-indicators state indicators)))

(defrecord TTPStore [state]
  ITTPStore
  (read-ttp [_ id]
    (ttp/handle-read-ttp state id))
  (create-ttp [_ login new-ttp]
    (ttp/handle-create-ttp state login new-ttp))
  (update-ttp [_ id login new-ttp]
    (ttp/handle-update-ttp state id login new-ttp))
  (delete-ttp [_ id]
    (ttp/handle-delete-ttp state id))
  (list-ttps [_ filter-map]))
