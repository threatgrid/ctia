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
  (create-judgements [_ new-judgements ident]
    (ju/handle-create state new-judgements ident))
  (add-indicator-to-judgement [_ judgement-id indicator-rel ident]
    (ju/handle-add-indicator-to state judgement-id indicator-rel ident))
  (read-judgement [_ id ident]
    (ju/handle-read state id ident))
  (delete-judgement [_ id ident]
    (ju/handle-delete state id ident))
  (list-judgements [_ filter-map ident params]
    (ju/handle-list state filter-map ident params))
  (list-judgements-by-observable [this observable ident params]
    (ju/handle-list state {[:observable :type]  (:type observable)
                           [:observable :value] (:value observable)} ident params))
  (calculate-verdict [_ observable ident]
    (ju/handle-calculate-verdict state observable ident))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (ju/handle-query-string-search state query filtermap ident params)))

(defrecord RelationshipStore [state]
  IRelationshipStore
  (create-relationships [_ new-relationships ident]
    (rel/handle-create state new-relationships ident))
  (read-relationship [_ id ident]
    (rel/handle-read state id ident))
  (delete-relationship [_ id ident]
    (rel/handle-delete state id ident))
  (list-relationships [_ filter-map ident params]
    (rel/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (rel/handle-query-string-search state query filtermap ident params)))

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedbacks [_ new-feedbacks ident]
    (fe/handle-create state new-feedbacks ident))
  (read-feedback [_ id ident]
    (fe/handle-read state id ident))
  (delete-feedback [_ id ident]
    (fe/handle-delete state id ident))
  (list-feedback [_ filter-map ident params]
    (fe/handle-list state filter-map ident params)))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicators [_ new-indicators ident]
    (in/handle-create state new-indicators ident))
  (update-indicator [_ id new-indicator ident]
    (in/handle-update state id new-indicator ident))
  (read-indicator [_ id ident]
    (in/handle-read state id ident))
  (delete-indicator [_ id ident]
    (in/handle-delete state id ident))
  (list-indicators [_ filter-map ident params]
    (in/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (in/handle-query-string-search state query filtermap ident params)))

(defrecord TTPStore [state]
  ITTPStore
  (read-ttp [_ id ident]
    (ttp/handle-read state id ident))
  (create-ttps [_ new-ttps ident]
    (ttp/handle-create state new-ttps ident))
  (update-ttp [_ id new-ttp ident]
    (ttp/handle-update state id new-ttp ident))
  (delete-ttp [_ id ident]
    (ttp/handle-delete state id ident))
  (list-ttps [_ filter-map ident params]
    (ttp/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (ttp/handle-query-string-search state query filtermap ident params)))

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id ident]
    (ac/handle-read state id ident))
  (create-actors [_ new-actors ident]
    (ac/handle-create state new-actors ident))
  (update-actor [_ id actor ident]
    (ac/handle-update state id actor ident))
  (delete-actor [_ id ident]
    (ac/handle-delete state id ident))
  (list-actors [_ filter-map ident params]
    (ac/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (ac/handle-query-string-search state query filtermap ident params)))

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id ident]
    (ca/handle-read state id ident))
  (create-campaigns [_ new-campaigns ident]
    (ca/handle-create state new-campaigns ident))
  (update-campaign [_ id new-campaign ident]
    (ca/handle-update state id new-campaign ident))
  (delete-campaign [_ id ident]
    (ca/handle-delete state id ident))
  (list-campaigns [_ filter-map ident params]
    (ca/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (ca/handle-query-string-search state query filtermap ident params)))

(defrecord COAStore [state]
  ICOAStore
  (read-coa [_ id ident]
    (coa/handle-read state id ident))
  (create-coas [_ new-coas ident]
    (coa/handle-create state new-coas ident))
  (update-coa [_ id new-coa ident]
    (coa/handle-update state id new-coa ident))
  (delete-coa [_ id ident]
    (coa/handle-delete state id ident))
  (list-coas [_ filter-map ident params]
    (coa/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (coa/handle-query-string-search state query filtermap ident params)))

(defrecord DataTableStore [state]
  IDataTableStore
  (read-data-table [_ id ident]
    (dt/handle-read state id ident))
  (create-data-tables [_ new-data-tables ident]
    (dt/handle-create state new-data-tables ident))
  (delete-data-table [_ id ident]
    (dt/handle-delete state id ident))
  (list-data-tables [_ filter-map ident params]
    (dt/handle-list state filter-map ident params)))

(defrecord IncidentStore [state]
  IIncidentStore
  (read-incident [_ id ident]
    (inc/handle-read state id ident))
  (create-incidents [_ new-incidents ident]
    (inc/handle-create state new-incidents ident))
  (update-incident [_ id new-incident ident]
    (inc/handle-update state id new-incident ident))
  (delete-incident [_ id ident]
    (inc/handle-delete state id ident))
  (list-incidents [_ filter-map ident params]
    (inc/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (inc/handle-query-string-search state query filtermap ident params)))

(defrecord ExploitTargetStore [state]
  IExploitTargetStore
  (read-exploit-target [_ id ident]
    (et/handle-read state id ident))
  (create-exploit-targets [_ new-exploit-targets ident]
    (et/handle-create state new-exploit-targets ident))
  (update-exploit-target [_ id new-exploit-target ident]
    (et/handle-update state id new-exploit-target ident))
  (delete-exploit-target [_ id ident]
    (et/handle-delete state id ident))
  (list-exploit-targets [_ filter-map ident params]
    (et/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (et/handle-query-string-search state query filtermap ident params)))

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
  (read-sighting [_ id ident]
    (sig/handle-read state id ident))
  (create-sightings [_ new-sightings ident]
    (sig/handle-create state new-sightings ident))
  (update-sighting [_ id sighting ident]
    (sig/handle-update state id sighting ident))
  (delete-sighting [_ id ident]
    (sig/handle-delete state id ident))
  (list-sightings [_ filter-map ident params]
    (sig/handle-list state filter-map ident params))
  (list-sightings-by-observables [_ observables ident params]
    (sig/handle-list-by-observables state observables ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (sig/handle-query-string-search-sightings state query filtermap ident params)))

(defrecord EventStore [state]
  IEventStore
  (create-events [this new-events]
    (ev/handle-create state new-events))
  (list-events [this filter-map ident params]
    (ev/handle-list state filter-map ident params)))
