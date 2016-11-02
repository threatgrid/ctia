(ns ctia.stores.es.store
  (:require [ctia.lib.es.index :as es-index :refer [connect ESConnState]]
            [schema.core :as s]
            [ctia.stores.es
             [actor :as ac]
             [campaign :as ca]
             [coa :as coa]
             [data-table :as dt]
             [exploit-target :as et]
             [feedback :as fe]
             [identity :as id]
             [incident :as inc]
             [indicator :as in]
             [judgement :as ju]
             [relationship :as rel]
             [verdict :as ve]
             [mapping :refer [store-mappings]]
             [sighting :as sig]
             [ttp :as ttp]
             [bundle :as bu]]
            [ctia.store :refer [IActorStore
                                ICampaignStore
                                ICOAStore
                                IDataTableStore
                                IExploitTargetStore
                                IFeedbackStore
                                IIdentityStore
                                IIncidentStore
                                IIndicatorStore
                                IJudgementStore
                                IRelationshipStore
                                IVerdictStore
                                ISightingStore
                                ITTPStore
                                IBundleStore]]))

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
  (create-judgement [_ new-judgement-chan]
    (ju/handle-create-judgement state new-judgement-chan))
  (add-indicator-to-judgement [_ judgement-id indicator-rel]
    (ju/handle-add-indicator-to-judgement state judgement-id indicator-rel))
  (read-judgement [_ id]
    (ju/handle-read-judgement state id))
  (delete-judgement [_ id-chan]
    (ju/handle-delete-judgement state id-chan))
  (list-judgements [_ filter-map params]
    (ju/handle-list-judgements state filter-map params))
  (list-judgements-by-observable [this observable params]
    (ju/handle-list-judgements state {[:observable :type]  (:type observable)
                                      [:observable :value] (:value observable)} params))
  (calculate-verdict [_ observable]
    (ju/handle-calculate-verdict state observable)))

(defrecord RelationshipStore [state]
  IRelationshipStore
  (create-relationship [_ new-relationship-chan]
    (rel/handle-create-relationship state new-relationship-chan))
  (read-relationship [_ id]
    (rel/handle-read-relationship state id))
  (delete-relationship [_ id-chan]
    (rel/handle-delete-relationship state id-chan))
  (list-relationships [_ filter-map params]
    (rel/handle-list-relationships state filter-map params)))

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
  (create-feedback [_ new-feedback-chan]
    (fe/handle-create-feedback state new-feedback-chan))
  (read-feedback [_ id]
    (fe/handle-read-feedback state id))
  (delete-feedback [_ id-chan]
    (fe/handle-delete-feedback state id-chan))
  (list-feedback [_ filter-map params]
    (fe/handle-list-feedback state filter-map params)))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicator [_ new-indicator-chan]
    (in/handle-create-indicator state new-indicator-chan))
  (update-indicator [_ id indicator-chan]
    (in/handle-update-indicator state id indicator-chan))
  (read-indicator [_ id]
    (in/handle-read-indicator state id))
  (delete-indicator [_ id-chan]
    (in/handle-delete-indicator state id-chan))
  (list-indicators [_ filter-map params]
    (in/handle-list-indicators state filter-map params))
  (list-indicators-by-judgements [_ judgements params]
    (in/handle-list-indicators-by-judgements state judgements params)))

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

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id]
    (ac/handle-read-actor state id))
  (create-actor [_ new-actor-chan]
    (ac/handle-create-actor state new-actor-chan))
  (update-actor [_ id actor-chan]
    (ac/handle-update-actor state id actor-chan))
  (delete-actor [_ id-chan]
    (ac/handle-delete-actor state id-chan))
  (list-actors [_ filter-map params]
    (ac/handle-list-actors state filter-map params)))

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id]
    (ca/handle-read-campaign state id))
  (create-campaign [_ new-campaign-chan]
    (ca/handle-create-campaign state new-campaign-chan))
  (update-campaign [_ id new-campaign-chan]
    (ca/handle-update-campaign state id new-campaign-chan))
  (delete-campaign [_ id-chan]
    (ca/handle-delete-campaign state id-chan))
  (list-campaigns [_ filter-map params]
    (ca/handle-list-campaigns state filter-map params)))

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
    (dt/handle-read-data-table state id))
  (create-data-table [_ new-data-table-chan]
    (dt/handle-create-data-table state new-data-table-chan))
  (delete-data-table [_ id-chan]
    (dt/handle-delete-data-table state id-chan))
  (list-data-tables [_ filter-map params]
    (dt/handle-list-data-tables state filter-map params)))

(defrecord IncidentStore [state]
  IIncidentStore
  (read-incident [_ id]
    (inc/handle-read-incident state id))
  (create-incident [_ new-incident-chan]
    (inc/handle-create-incident state new-incident-chan))
  (update-incident [_ id incident-chan]
    (inc/handle-update-incident state id incident-chan))
  (delete-incident [_ id-chan]
    (inc/handle-delete-incident state id-chan))
  (list-incidents [_ filter-map params]
    (inc/handle-list-incidents state filter-map params)))

(defrecord ExploitTargetStore [state]
  IExploitTargetStore
  (read-exploit-target [_ id]
    (et/handle-read-exploit-target state id))
  (create-exploit-target [_ new-exploit-target-chan]
    (et/handle-create-exploit-target state new-exploit-target-chan))
  (update-exploit-target [_ id exploit-target-chan]
    (et/handle-update-exploit-target state id exploit-target-chan))
  (delete-exploit-target [_ id-chan]
    (et/handle-delete-exploit-target state id-chan))
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
  (create-sighting [_ new-sighting-chan]
    (sig/handle-create-sighting state new-sighting-chan))
  (update-sighting [_ id sighting-chan]
    (sig/handle-update-sighting state id sighting-chan))
  (delete-sighting [_ id-chan]
    (sig/handle-delete-sighting state id-chan))
  (list-sightings [_ filter-map params]
    (sig/handle-list-sightings state filter-map params))
  (list-sightings-by-observables [_ observables params]
    (sig/handle-list-sightings-by-observables state observables params)))

(defrecord BundleStore [state]
  IBundleStore
  (read-bundle [_ id]
    (bu/handle-read-bundle state id))
  (create-bundle [_ new-bundle-chan]
    (bu/handle-create-bundle state new-bundle-chan))
  (delete-bundle [_ id-chan]
    (bu/handle-delete-bundle state id-chan)))
