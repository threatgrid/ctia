(ns ctia.stores.es.store
  (:require [clj-momo.lib.es
             [conn :refer [connect]]
             [index :as es-index]
             [schemas :refer [ESConnState]]]
            [ctia.store
             :refer
             [IActorStore
              ICampaignStore
              ICOAStore
              IDataTableStore
              IEventStore
              IExploitTargetStore
              IFeedbackStore
              IIdentityStore
              IIncidentStore
              IIndicatorStore
              IJudgementStore
              IQueryStringSearchableStore
              IRelationshipStore
              ISightingStore
              ITTPStore]]
            [ctia.stores.es
             [actor :as ac]
             [campaign :as ca]
             [coa :as coa]
             [data-table :as dt]
             [event :as ev]
             [exploit-target :as et]
             [feedback :as fe]
             [identity :as id]
             [incident :as inc]
             [indicator :as in]
             [judgement :as ju]
             [mapping :refer [store-mappings store-settings]]
             [relationship :as rel]
             [sighting :as sig]
             [ttp :as ttp]]
            [schema.core :as s]))

(defn delete-state-indexes [{:keys [conn index config]}]
  (when conn
    (es-index/delete! conn (str index "*"))))

(s/defn init-store-conn :- ESConnState
  "initiate an ES store connection returns
   a map containing a connection manager, dedicated store index properties"
  [{:keys [entity indexname shards replicas] :as props}]

  (let [settings {:number_of_shards shards
                  :number_of_replicas replicas}]
    {:index indexname
     :props props
     :config {:settings (merge store-settings settings)
              :mappings (get store-mappings entity)}
     :conn (connect props)}))

(s/defn init! :- ESConnState
  "initiate an ES Store connection,
   put the index template, return an ESConnState"
  [props]
  (let [{:keys [conn index config] :as conn-state} (init-store-conn props)]
    (es-index/create-template! conn index config)
    conn-state))

(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgements [_ new-judgements]
    (ju/handle-create state new-judgements))
  (add-indicator-to-judgement [_ judgement-id indicator-rel]
    (ju/handle-add-indicator-to state judgement-id indicator-rel))
  (read-judgement [_ id]
    (ju/handle-read state id))
  (delete-judgement [_ id]
    (ju/handle-delete state id))
  (list-judgements [_ filter-map params]
    (ju/handle-list state filter-map params))
  (list-judgements-by-observable [this observable params]
    (ju/handle-list state {[:observable :type]  (:type observable)
                           [:observable :value] (:value observable)} params))
  (calculate-verdict [_ observable]
    (ju/handle-calculate-verdict state observable))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap params]
    (ju/handle-query-string-search state query filtermap params)))

(defrecord RelationshipStore [state]
  IRelationshipStore
  (create-relationships [_ new-relationships]
    (rel/handle-create state new-relationships))
  (read-relationship [_ id]
    (rel/handle-read state id))
  (delete-relationship [_ id]
    (rel/handle-delete state id))
  (list-relationships [_ filter-map params]
    (rel/handle-list state filter-map params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap params]
    (rel/handle-query-string-search state query filtermap params)))

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedbacks [_ new-feedbacks]
    (fe/handle-create state new-feedbacks))
  (read-feedback [_ id]
    (fe/handle-read state id))
  (delete-feedback [_ id]
    (fe/handle-delete state id))
  (list-feedback [_ filter-map params]
    (fe/handle-list state filter-map params)))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicators [_ new-indicators]
    (in/handle-create state new-indicators))
  (update-indicator [_ id new-indicator]
    (in/handle-update state id new-indicator))
  (read-indicator [_ id]
    (in/handle-read state id))
  (delete-indicator [_ id]
    (in/handle-delete state id))
  (list-indicators [_ filter-map params]
    (in/handle-list state filter-map params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap params]
    (in/handle-query-string-search state query filtermap params)))

(defrecord TTPStore [state]
  ITTPStore
  (read-ttp [_ id]
    (ttp/handle-read state id))
  (create-ttps [_ new-ttps]
    (ttp/handle-create state new-ttps))
  (update-ttp [_ id new-ttp]
    (ttp/handle-update state id new-ttp))
  (delete-ttp [_ id]
    (ttp/handle-delete state id))
  (list-ttps [_ filter-map params]
    (ttp/handle-list state filter-map params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap params]
    (ttp/handle-query-string-search state query filtermap params)))

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id]
    (ac/handle-read state id))
  (create-actors [_ new-actors]
    (ac/handle-create state new-actors))
  (update-actor [_ id actor]
    (ac/handle-update state id actor))
  (delete-actor [_ id]
    (ac/handle-delete state id))
  (list-actors [_ filter-map params]
    (ac/handle-list state filter-map params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap params]
    (ac/handle-query-string-search state query filtermap params)))

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id]
    (ca/handle-read state id))
  (create-campaigns [_ new-campaigns]
    (ca/handle-create state new-campaigns))
  (update-campaign [_ id new-campaign]
    (ca/handle-update state id new-campaign))
  (delete-campaign [_ id]
    (ca/handle-delete state id))
  (list-campaigns [_ filter-map params]
    (ca/handle-list state filter-map params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap params]
    (ca/handle-query-string-search state query filtermap params)))

(defrecord COAStore [state]
  ICOAStore
  (read-coa [_ id]
    (coa/handle-read state id))
  (create-coas [_ new-coas]
    (coa/handle-create state new-coas))
  (update-coa [_ id new-coa]
    (coa/handle-update state id new-coa))
  (delete-coa [_ id]
    (coa/handle-delete state id))
  (list-coas [_ filter-map params]
    (coa/handle-list state filter-map params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap params]
    (coa/handle-query-string-search state query filtermap params)))

(defrecord DataTableStore [state]
  IDataTableStore
  (read-data-table [_ id]
    (dt/handle-read state id))
  (create-data-tables [_ new-data-tables]
    (dt/handle-create state new-data-tables))
  (delete-data-table [_ id]
    (dt/handle-delete state id))
  (list-data-tables [_ filter-map params]
    (dt/handle-list state filter-map params)))

(defrecord IncidentStore [state]
  IIncidentStore
  (read-incident [_ id]
    (inc/handle-read state id))
  (create-incidents [_ new-incidents]
    (inc/handle-create state new-incidents))
  (update-incident [_ id new-incident]
    (inc/handle-update state id new-incident))
  (delete-incident [_ id]
    (inc/handle-delete state id))
  (list-incidents [_ filter-map params]
    (inc/handle-list state filter-map params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap params]
    (inc/handle-query-string-search state query filtermap params)))

(defrecord ExploitTargetStore [state]
  IExploitTargetStore
  (read-exploit-target [_ id]
    (et/handle-read state id))
  (create-exploit-targets [_ new-exploit-targets]
    (et/handle-create state new-exploit-targets))
  (update-exploit-target [_ id new-exploit-target]
    (et/handle-update state id new-exploit-target))
  (delete-exploit-target [_ id]
    (et/handle-delete state id))
  (list-exploit-targets [_ filter-map params]
    (et/handle-list state filter-map params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap params]
    (et/handle-query-string-search state query filtermap params)))

(defrecord IdentityStore [state]
  IIdentityStore
  (read-identity [_ login]
    (id/handle-read state login))
  (create-identity [_ new-identity]
    (id/handle-create state new-identity))
  (delete-identity [_ org-id role]
    (id/handle-delete state org-id role)))

(defrecord SightingStore [state]
  ISightingStore
  (read-sighting [_ id]
    (sig/handle-read state id))
  (create-sightings [_ new-sightings]
    (sig/handle-create state new-sightings))
  (update-sighting [_ id sighting]
    (sig/handle-update state id sighting))
  (delete-sighting [_ id]
    (sig/handle-delete state id))
  (list-sightings [_ filter-map params]
    (sig/handle-list state filter-map params))
  (list-sightings-by-observables [_ observables params]
    (sig/handle-list-by-observables state observables params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap params]
    (sig/handle-query-string-search-sightings state query filtermap params)))

(defrecord EventStore [state]
  IEventStore
  (create-events [this new-events]
    (ev/handle-create state new-events))
  (list-events [this filter-map params]
    (ev/handle-list state filter-map params)))
