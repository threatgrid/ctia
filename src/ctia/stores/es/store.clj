(ns ctia.stores.es.store
  (:require [clj-momo.lib.es.index :as es-index]
            [ctia.store
             :refer
             [IActorStore
              IAttackPatternStore
              ICampaignStore
              ICOAStore
              IDataTableStore
              IEventStore
              IExploitTargetStore
              IFeedbackStore
              IIdentityStore
              IIncidentStore
              IIndicatorStore
              IInvestigationStore
              IJudgementStore
              IMalwareStore
              IQueryStringSearchableStore
              IRelationshipStore
              IScratchpadStore
              ISightingStore
              IToolStore]]
            [ctia.stores.es
             [actor :as ac]
             [attack-pattern :as attack]
             [campaign :as ca]
             [coa :as coa]
             [data-table :as dt]
             [event :as ev]
             [exploit-target :as et]
             [feedback :as fe]
             [identity :as id]
             [incident :as inc]
             [indicator :as in]
             [investigation :as inv]
             [scratchpad :as scr]
             [judgement :as ju]
             [malware :as malware]
             [mapping :refer [store-mappings store-settings]]
             [relationship :as rel]
             [sighting :as sig]
             [tool :as tool]]
            [schema.core :as s]))

(defn delete-state-indexes [{:keys [conn index config]}]
  (when conn
    (es-index/delete! conn (str index "*"))))

(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgements [_ new-judgements ident params]
    (ju/handle-create state new-judgements ident params))
  (add-indicator-to-judgement [_ judgement-id indicator-rel ident]
    (ju/handle-add-indicator-to state judgement-id indicator-rel ident))
  (read-judgement [_ id ident params]
    (ju/handle-read state id ident params))
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
  (create-relationships [_ new-relationships ident params]
    (rel/handle-create state new-relationships ident params))
  (update-relationship [_ id new-relationship ident]
    (rel/handle-update state id new-relationship ident))
  (read-relationship [_ id ident params]
    (rel/handle-read state id ident params))
  (delete-relationship [_ id ident]
    (rel/handle-delete state id ident))
  (list-relationships [_ filter-map ident params]
    (rel/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (rel/handle-query-string-search state query filtermap ident params)))

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedbacks [_ new-feedbacks ident params]
    (fe/handle-create state new-feedbacks ident params))
  (read-feedback [_ id ident params]
    (fe/handle-read state id ident params))
  (delete-feedback [_ id ident]
    (fe/handle-delete state id ident))
  (list-feedback [_ filter-map ident params]
    (fe/handle-list state filter-map ident params)))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicators [_ new-indicators ident params]
    (in/handle-create state new-indicators ident params))
  (update-indicator [_ id new-indicator ident]
    (in/handle-update state id new-indicator ident))
  (read-indicator [_ id ident params]
    (in/handle-read state id ident params))
  (delete-indicator [_ id ident]
    (in/handle-delete state id ident))
  (list-indicators [_ filter-map ident params]
    (in/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (in/handle-query-string-search state query filtermap ident params)))

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id ident params]
    (ac/handle-read state id ident params))
  (create-actors [_ new-actors ident params]
    (ac/handle-create state new-actors ident params))
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
  (read-campaign [_ id ident params]
    (ca/handle-read state id ident params))
  (create-campaigns [_ new-campaigns ident params]
    (ca/handle-create state new-campaigns ident params))
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
  (read-coa [_ id ident params]
    (coa/handle-read state id ident params))
  (create-coas [_ new-coas ident params]
    (coa/handle-create state new-coas ident params))
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
  (read-data-table [_ id ident params]
    (dt/handle-read state id ident params))
  (create-data-tables [_ new-data-tables ident params]
    (dt/handle-create state new-data-tables ident params))
  (delete-data-table [_ id ident]
    (dt/handle-delete state id ident))
  (list-data-tables [_ filter-map ident params]
    (dt/handle-list state filter-map ident params)))

(defrecord IncidentStore [state]
  IIncidentStore
  (read-incident [_ id ident params]
    (inc/handle-read state id ident params))
  (create-incidents [_ new-incidents ident params]
    (inc/handle-create state new-incidents ident params))
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
  (read-exploit-target [_ id ident params]
    (et/handle-read state id ident params))
  (create-exploit-targets [_ new-exploit-targets ident params]
    (et/handle-create state new-exploit-targets ident params))
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
    (id/handle-delete state org-id)))

(defrecord SightingStore [state]
  ISightingStore
  (read-sighting [_ id ident params]
    (sig/handle-read state id ident params))
  (create-sightings [_ new-sightings ident params]
    (sig/handle-create state new-sightings ident params))
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

(defrecord AttackPatternStore [state]
  IAttackPatternStore
  (read-attack-pattern [_ id ident params]
    (attack/handle-read state id ident params))
  (create-attack-patterns [_ new-attack-patterns ident params]
    (attack/handle-create state new-attack-patterns ident params))
  (update-attack-pattern [_ id attack-pattern ident]
    (attack/handle-update state id attack-pattern ident))
  (delete-attack-pattern [_ id ident]
    (attack/handle-delete state id ident))
  (list-attack-patterns [_ filter-map ident params]
    (attack/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (attack/handle-query-string-search state query filtermap ident params)))

(defrecord MalwareStore [state]
  IMalwareStore
  (read-malware [_ id ident params]
    (malware/handle-read state id ident params))
  (create-malwares [_ new-malwares ident params]
    (malware/handle-create state new-malwares ident params))
  (update-malware [_ id malware ident]
    (malware/handle-update state id malware ident))
  (delete-malware [_ id ident]
    (malware/handle-delete state id ident))
  (list-malwares [_ filter-map ident params]
    (malware/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (malware/handle-query-string-search state query filtermap ident params)))

(defrecord ToolStore [state]
  IToolStore
  (read-tool [_ id ident params]
    (tool/handle-read state id ident params))
  (create-tools [_ new-tools ident params]
    (tool/handle-create state new-tools ident params))
  (update-tool [_ id tool ident]
    (tool/handle-update state id tool ident))
  (delete-tool [_ id ident]
    (tool/handle-delete state id ident))
  (list-tools [_ filter-map ident params]
    (tool/handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (tool/handle-query-string-search state query filtermap ident params)))

(defrecord EventStore [state]
  IEventStore
  (create-events [this new-events]
    (ev/handle-create state new-events))
  (list-events [this filter-map ident params]
    (ev/handle-list state filter-map ident params)))

(defrecord InvestigationStore [state]
  IInvestigationStore
  (read-investigation [_ id ident params]
    (inv/handle-read state id ident params))
  (create-investigations [_ new-investigations ident params]
    (inv/handle-create state new-investigations ident params))
  (update-investigation [_ id investigation ident]
    (inv/handle-update state id investigation ident))
  (delete-investigation [this id ident]
    (inv/handle-delete state id ident))
  (list-investigations [this filtermap ident params]
    (inv/handle-list state filtermap ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (inv/handle-query-string-search state query filtermap ident params)))

(defrecord ScratchpadStore [state]
  IScratchpadStore
  (read-scratchpad [_ id ident params]
    (scr/handle-read state id ident params))
  (create-scratchpads [_ new-scratchpads ident params]
    (scr/handle-create state new-scratchpads ident params))
  (update-scratchpad [_ id scratchpad ident]
    (scr/handle-update state id scratchpad ident))
  (delete-scratchpad [this id ident]
    (scr/handle-delete state id ident))
  (list-scratchpads [this filtermap ident params]
    (scr/handle-list state filtermap ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (scr/handle-query-string-search state query filtermap ident params)))
