(ns ctia.stores.es.store
  (:require
   [schema.core :as s]
   [ctia.properties :refer [properties]]
   [ctia.stores.es.mapping :refer [store-mappings]]
   [ctia.lib.es.index :refer [ESConnState connect] :as es-index]
   [ctia.stores.es.judgement :as ju]
   [ctia.stores.es.feedback  :as fe]
   [ctia.stores.es.indicator :as in]
   [ctia.stores.es.ttp :as ttp]
   [ctia.stores.es.actor :as ac]
   [ctia.stores.es.campaign :as ca]
   [ctia.stores.es.coa :as coa]
   [ctia.stores.es.incident :as inc]
   [ctia.stores.es.exploit-target :as et]
   [ctia.stores.es.sighting :as sig]
   [ctia.stores.es.identity :as id]
   [ctia.store :refer [IActorStore
                       IJudgementStore
                       IIndicatorStore
                       IExploitTargetStore
                       IFeedbackStore
                       ITTPStore
                       ICampaignStore
                       ICOAStore
                       ISightingStore
                       IIncidentStore
                       IIdentityStore]]))

(defonce es-state (atom nil))

(defn read-store-index-spec []
  "read es store index config properties, returns an option map"
  (get-in @properties [:ctia :store :es]))

(s/defn init-store-conn :- ESConnState []
  "initiate an ES store connection returns a map containing transport,
   mapping, and the configured index name"
  (let [props (read-store-index-spec)]
    {:index (:indexname props)
     :props props
     :mapping store-mappings
     :conn (connect props)}))

(defn init! []
  (let [state (reset! es-state (init-store-conn))]
    (es-index/create! (:conn state)
                      (:index state)
                      (:mapping state))))

(defn uninitialized? []
  (nil? @es-state))

(defn shutdown! []
  (reset! es-state nil))

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
  (list-judgements [_ filter-map]
    (ju/handle-list-judgements state filter-map))
  (list-judgements-by-observable [this observable]
    (ju/handle-list-judgements state {[:observable :type]  (:type observable)
                                      [:observable :value] (:value observable)}))
  (calculate-verdict [_ observable]
    (ju/handle-calculate-verdict state observable)))

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback]
    (fe/handle-create-feedback state new-feedback))
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
  (list-indicators-by-judgements [_ judgements]
    (in/handle-list-indicators-by-judgements state judgements)))

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
  (list-sightings [_ filter-map]
    (sig/handle-list-sightings state filter-map))
  (list-sightings-by-indicators [_ indicators]
    (sig/handle-list-sightings-by-indicators state indicators))
  (list-sightings-by-observables [_ observables]
    (sig/handle-list-sightings-by-observables state observables)))
