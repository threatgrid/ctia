(ns ctia.stores.atom.store
  (:require
   [durable-atom.core :refer [durable-atom]]
   [ctia.store :refer :all]
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
    [relationship :as relationship]
    [sighting :as sighting]
    [ttp :as ttp]
    [verdict :as verdict]
    [bundle :as bundle]
    [data-table :as data-table]]))

(defn init! [{:keys [path mode] :as _props_}]
  (if (= mode :durable)
    (doto (durable-atom path)
      (swap! #(or % {})))
    (atom {})))

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id]
    (actor/handle-read-actor state id))
  (create-actor [_ new-actor-chan]
    (actor/handle-create-actor state new-actor-chan))
  (update-actor [_ id actor-chan]
    (actor/handle-update-actor state id actor-chan))
  (delete-actor [_ id-chan]
    (actor/handle-delete-actor state id-chan))
  (list-actors [_ filter-map params]
    (actor/handle-list-actors state filter-map params)))

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id]
    (campaign/handle-read-campaign state id))
  (create-campaign [_ new-campaign-chan]
    (campaign/handle-create-campaign state new-campaign-chan))
  (update-campaign [_ id new-campaign-chan]
    (campaign/handle-update-campaign state id new-campaign-chan))
  (delete-campaign [_ id-chan]
    (campaign/handle-delete-campaign state id-chan))
  (list-campaigns [_ filter-map params]
    (campaign/handle-list-campaigns state filter-map params)))

(defrecord COAStore [state]
  ICOAStore
  (read-coa [_ id]
    (coa/handle-read-coa state id))
  (create-coa [_ new-coa-chan]
    (coa/handle-create-coa state new-coa-chan))
  (update-coa [_ id coa-chan]
    (coa/handle-update-coa state id coa-chan))
  (delete-coa [_ id-chan]
    (coa/handle-delete-coa state id-chan))
  (list-coas [_ filter-map params]
    (coa/handle-list-coas state filter-map params)))

(defrecord DataTableStore [state]
  IDataTableStore
  (read-data-table [_ id]
    (data-table/handle-read-data-table state id))
  (create-data-table [_ new-data-table-chan]
    (data-table/handle-create-data-table state new-data-table-chan))
  (delete-data-table [_ id-chan]
    (data-table/handle-delete-data-table state id-chan))
  (list-data-tables [_ filter-map params]
    (data-table/handle-list-data-tables state filter-map params)))

(defrecord ExploitTargetStore [state]
  IExploitTargetStore
  (read-exploit-target [_ id]
    (expl-tar/handle-read-exploit-target state id))
  (create-exploit-target [_ new-exploit-target-chan]
    (expl-tar/handle-create-exploit-target state new-exploit-target-chan))
  (update-exploit-target [_ id exploit-target-chan]
    (expl-tar/handle-update-exploit-target state id exploit-target-chan))
  (delete-exploit-target [_ id-chan]
    (expl-tar/handle-delete-exploit-target state id-chan))
  (list-exploit-targets [_ filter-map params]
    (expl-tar/handle-list-exploit-targets state filter-map params)))

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback-chan]
    (feedback/handle-create-feedback state new-feedback-chan))
  (read-feedback [_ id]
    (feedback/handle-read-feedback state id))
  (list-feedback [_ filter-map params]
    (feedback/handle-list-feedback state filter-map params))
  (delete-feedback [_ id-chan]
    (feedback/handle-delete-feedback state id-chan)))

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
  (create-incident [_ new-incident-chan]
    (incident/handle-create-incident state new-incident-chan))
  (update-incident [_ id incident-chan]
    (incident/handle-update-incident state id incident-chan))
  (delete-incident [_ id-chan]
    (incident/handle-delete-incident state id-chan))
  (list-incidents [_ filter-map params]
    (incident/handle-list-incidents state filter-map params)))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicator [_ new-indicator-chan]
    (indicator/handle-create-indicator state new-indicator-chan))
  (update-indicator [_ id indicator-chan]
    (indicator/handle-update-indicator state id indicator-chan))
  (read-indicator [_ id]
    (indicator/handle-read-indicator state id))
  (delete-indicator [_ id-chan]
    (indicator/handle-delete-indicator state id-chan))
  (list-indicators [_ filter-map params]
    (indicator/handle-list-indicators state filter-map params))
  (list-indicators-by-judgements [_ judgements params]
    (indicator/handle-list-indicators-by-judgements state judgements params)))

(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgement [_ new-judgement-chan]
    (judgement/handle-create-judgement state new-judgement-chan))
  (read-judgement [_ id]
    (judgement/handle-read-judgement state id))
  (delete-judgement [_ id-chan]
    (judgement/handle-delete-judgement state id-chan))
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

(defrecord RelationshipStore [state]
  IRelationshipStore
  (create-relationship [_ new-relationship-chan]
    (relationship/handle-create-relationship state new-relationship-chan))
  (read-relationship [_ id]
    (relationship/handle-read-relationship state id))
  (delete-relationship [_ id-chan]
    (relationship/handle-delete-relationship state id-chan))
  (list-relationships [_ filter-map params]
    (relationship/handle-list-relationships state filter-map params)))

(defrecord VerdictStore [state]
  IVerdictStore
  (create-verdict [_ new-verdict-chan]
    (verdict/handle-create state new-verdict-chan))
  (read-verdict [_ id]
    (verdict/handle-read state id))
  (delete-verdict [_ id-chan]
    (verdict/handle-delete state id-chan))
  (list-verdicts [_ filter-map params]
    (verdict/handle-list state filter-map params)))

(defrecord SightingStore [state]
  ISightingStore
  (read-sighting [_ id]
    (sighting/handle-read-sighting state id))
  (create-sighting [_ new-sighting-chan]
    (sighting/handle-create-sighting state new-sighting-chan))
  (update-sighting [_ id sighting-chan]
    (sighting/handle-update-sighting state id sighting-chan))
  (delete-sighting [_ id-chan]
    (sighting/handle-delete-sighting state id-chan))
  (list-sightings [_ filter-map params]
    (sighting/handle-list-sightings state filter-map params))
  (list-sightings-by-observables [_ observables params]
    (sighting/handle-list-sightings-by-observables state observables params)))

(defrecord TTPStore [state]
  ITTPStore
  (read-ttp [_ id]
    (ttp/handle-read-ttp state id))
  (create-ttp [_ new-ttp-chan]
    (ttp/handle-create-ttp state new-ttp-chan))
  (update-ttp [_ id ttp-chan]
    (ttp/handle-update-ttp state id ttp-chan))
  (delete-ttp [_ id-chan]
    (ttp/handle-delete-ttp state id-chan))
  (list-ttps [_ filter-map params]
    (ttp/handle-list-ttps state filter-map params)))

(defrecord BundleStore [state]
  IBundleStore
  (read-bundle [_ id]
    (bundle/handle-read-bundle state id))
  (create-bundle [_ new-bundle-chan]
    (bundle/handle-create-bundle state new-bundle-chan))
  (delete-bundle [_ id-chan]
    (bundle/handle-delete-bundle state id-chan)))
