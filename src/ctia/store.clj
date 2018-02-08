(ns ctia.store
  (:require [clojure.tools.logging :as log]))

(defprotocol IActorStore
  (create-actors [this new-actors ident params])
  (read-actor [this id ident params])
  (update-actor [this id actor ident])
  (delete-actor [this id ident])
  (list-actors [this filtermap ident params]))

(defprotocol IJudgementStore
  (create-judgements [this new-judgements ident params])
  (read-judgement [this id ident params])
  (delete-judgement [this id ident])
  (list-judgements [this filter-map ident params])
  (calculate-verdict [this observable ident])
  (list-judgements-by-observable [this observable ident params])
  (add-indicator-to-judgement [this judgement-id indicator-relationship ident]))

(defprotocol IIndicatorStore
  (create-indicators [this new-indicators ident params])
  (update-indicator [this id indicator ident])
  (read-indicator [this id ident params])
  (delete-indicator [this id ident])
  (list-indicators [this filtermap ident params]))

(defprotocol IExploitTargetStore
  (read-exploit-target [this id ident params])
  (create-exploit-targets [this new-exploit-targets ident params])
  (update-exploit-target [this id exploit-target ident])
  (delete-exploit-target [this id ident])
  (list-exploit-targets [this filtermap ident params]))

(defprotocol IFeedbackStore
  (read-feedback [this id ident params])
  (create-feedbacks [this new-feedbacks ident params])
  (delete-feedback [this id ident])
  (list-feedback [this filtermap ident params]))

(defprotocol ICampaignStore
  (read-campaign [this id ident params])
  (create-campaigns [this new-campaigns ident params])
  (update-campaign [this id campaign ident])
  (delete-campaign [this id ident])
  (list-campaigns [this filtermap ident params]))

(defprotocol ICOAStore
  (read-coa [this id ident params])
  (create-coas [this new-coas ident params])
  (update-coa [this id coa ident])
  (delete-coa [this id ident])
  (list-coas [this filtermap ident params]))

(defprotocol ISightingStore
  (read-sighting [this id ident params])
  (create-sightings [this new-sightings ident params])
  (update-sighting [this id sighting ident])
  (delete-sighting [this id ident])
  (list-sightings [this filtermap ident params])
  (list-sightings-by-observables [this observable ident params]))

(defprotocol IIncidentStore
  (read-incident [this id ident params])
  (create-incidents [this new-incidents ident params])
  (update-incident [this id incident ident])
  (delete-incident [this id ident])
  (list-incidents [this filtermap ident params]))

(defprotocol IRelationshipStore
  (read-relationship [this id ident params])
  (create-relationships [this new-relations ident params])
  (delete-relationship [this id ident])
  (list-relationships [this filtermap ident params]))

(defprotocol IIdentityStore
  (read-identity [this login])
  (create-identity [this new-identity])
  (delete-identity [this org-id role]))

(defprotocol IDataTableStore
  (read-data-table [this id ident params])
  (create-data-tables [this new-data-tables ident params])
  (delete-data-table [this id ident])
  (list-data-tables [this filtermap ident params]))

(defprotocol IAttackPatternStore
  (read-attack-pattern [this id ident params])
  (create-attack-patterns [this new-attack-patterns ident params])
  (update-attack-pattern [this id attack-pattern ident])
  (delete-attack-pattern [this id ident])
  (list-attack-patterns [this filtermap ident params]))

(defprotocol IMalwareStore
  (read-malware [this id ident params])
  (create-malwares [this new-malwares ident params])
  (update-malware [this id malware ident])
  (delete-malware [this id ident])
  (list-malwares [this filtermap ident params]))

(defprotocol IToolStore
  (read-tool [this id ident params])
  (create-tools [this new-tools ident params])
  (update-tool [this id tool ident])
  (delete-tool [this id ident])
  (list-tools [this filtermap ident params]))

(defprotocol IEventStore
  (create-events [this new-events])
  (list-events [this filtermap ident params]))

(defprotocol IInvestigationStore
  (read-investigation [this id ident params])
  (create-investigations [this new-investigations ident params])
  (update-investigation [this id investigation ident])
  (delete-investigation [this id ident])
  (list-investigations [this filtermap ident params]))

(defprotocol IScratchpadStore
  (read-scratchpad [this id ident params])
  (create-scratchpads [this new-scratchpads ident])
  (update-scratchpad [this id scratchpad ident])
  (delete-scratchpad [this id ident])
  (list-scratchpads [this filtermap ident params]))

(defprotocol IQueryStringSearchableStore
  (query-string-search [this query filtermap ident params]))

(def empty-stores {:judgement []
                   :indicator []
                   :feedback []
                   :campaign []
                   :actor []
                   :coa []
                   :data-table []
                   :exploit-target []
                   :sighting []
                   :incident []
                   :relationship []
                   :identity []
                   :attack-pattern []
                   :malware []
                   :tool []
                   :event []
                   :investigation []
                   :scratchpad []})

(defonce stores (atom empty-stores))

(defn write-store [store write-fn & args]
  (first (doall (map #(apply write-fn % args) (store @stores)))))

(defn read-store [store read-fn & args]
  (apply read-fn (first (get @stores store)) args))

(defn query-string-search-store [store read-fn & args]
  (log/debug "query-string-search-store args: " store read-fn args)
  (apply read-fn (first (get @stores store)) args))
