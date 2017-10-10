(ns ctia.store
  (:require [clojure.tools.logging :as log]))

(defprotocol IActorStore
  (read-actor [this id ident])
  (create-actors [this new-actors ident])
  (update-actor [this id actor ident])
  (delete-actor [this id ident])
  (list-actors [this filtermap ident params]))

(defprotocol IJudgementStore
  (create-judgements [this new-judgements ident])
  (read-judgement [this id ident])
  (delete-judgement [this id ident])
  (list-judgements [this filter-map ident params])
  (calculate-verdict [this observable ident])
  (list-judgements-by-observable [this observable ident params])
  (add-indicator-to-judgement [this judgement-id indicator-relationship ident]))

(defprotocol IIndicatorStore
  (create-indicators [this new-indicators ident])
  (update-indicator [this id indicator ident])
  (read-indicator [this id ident])
  (delete-indicator [this id ident])
  (list-indicators [this filtermap ident params]))

(defprotocol IExploitTargetStore
  (read-exploit-target [this id ident])
  (create-exploit-targets [this new-exploit-targets ident])
  (update-exploit-target [this id exploit-target ident])
  (delete-exploit-target [this id ident])
  (list-exploit-targets [this filtermap ident params]))

(defprotocol IFeedbackStore
  (read-feedback [this id ident])
  (create-feedbacks [this new-feedbacks ident])
  (delete-feedback [this id ident])
  (list-feedback [this filtermap ident params]))

(defprotocol ICampaignStore
  (read-campaign [this id ident])
  (create-campaigns [this new-campaigns ident])
  (update-campaign [this id campaign ident])
  (delete-campaign [this id ident])
  (list-campaigns [this filtermap ident params]))

(defprotocol ICOAStore
  (read-coa [this id ident])
  (create-coas [this new-coas ident])
  (update-coa [this id coa ident])
  (delete-coa [this id ident])
  (list-coas [this filtermap ident params]))

(defprotocol ISightingStore
  (read-sighting [this id ident])
  (create-sightings [this new-sightings ident])
  (update-sighting [this id sighting ident])
  (delete-sighting [this id ident])
  (list-sightings [this filtermap ident params])
  (list-sightings-by-observables [this observable ident params]))

(defprotocol IIncidentStore
  (read-incident [this id ident])
  (create-incidents [this new-incidents ident])
  (update-incident [this id incident ident])
  (delete-incident [this id ident])
  (list-incidents [this filtermap ident params]))

(defprotocol IRelationshipStore
  (read-relationship [this id ident])
  (create-relationships [this new-relations ident])
  (delete-relationship [this id ident])
  (list-relationships [this filtermap ident params]))

(defprotocol IIdentityStore
  (read-identity [this login])
  (create-identity [this new-identity])
  (delete-identity [this org-id role]))

(defprotocol IDataTableStore
  (read-data-table [this id ident])
  (create-data-tables [this new-data-tables ident])
  (delete-data-table [this id ident])
  (list-data-tables [this filtermap ident params]))

(defprotocol IAttackPatternStore
  (read-attack-pattern [this id ident])
  (create-attack-patterns [this new-attack-patterns ident])
  (update-attack-pattern [this id attack-pattern ident])
  (delete-attack-pattern [this id ident])
  (list-attack-patterns [this filtermap ident params]))

(defprotocol IMalwareStore
  (read-malware [this id ident])
  (create-malwares [this new-malwares ident])
  (update-malware [this id malware ident])
  (delete-malware [this id ident])
  (list-malwares [this filtermap ident params]))

(defprotocol IToolStore
  (read-tool [this id ident])
  (create-tools [this new-tools ident])
  (update-tool [this id tool ident])
  (delete-tool [this id ident])
  (list-tools [this filtermap ident params]))

(defprotocol IEventStore
  (create-events [this new-events])
  (list-events [this filtermap ident params]))

(defprotocol IQueryStringSearchableStore
  (query-string-search [this query filtermap ident params]))

(defonce stores (atom {:judgement []
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
                       :event []}))

(defn write-store [store write-fn & args]
  (first (doall (map #(apply write-fn % args) (store @stores)))))

(defn read-store [store read-fn & args]
  (apply read-fn (first (get @stores store)) args))

(defn query-string-search-store [store read-fn & args]
  (log/debug "query-string-search-store args: " store read-fn args)
  (apply read-fn (first (get @stores store)) args))
