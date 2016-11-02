(ns ctia.store
  (:require [clojure.tools.logging :as log]))

(defprotocol IActorStore
  (read-actor [this id])
  (create-actors [this new-actors])
  (update-actor [this id actor])
  (delete-actor [this id])
  (list-actors [this filtermap params]))

(defprotocol IJudgementStore
  (create-judgements [this new-judgements])
  (read-judgement [this id])
  (delete-judgement [this id])
  (list-judgements [this filter-map params])
  (calculate-verdict [this observable])
  (list-judgements-by-observable [this observable params])
  (add-indicator-to-judgement [this judgement-id indicator-relationship]))

(defprotocol IVerdictStore
  (create-verdicts [this new-verdicts])
  (read-verdict [this id])
  (delete-verdict [this id])
  (list-verdicts [this filter-map params]))

(defprotocol IIndicatorStore
  (create-indicators [this new-indicators])
  (update-indicator [this id indicator])
  (read-indicator [this id])
  (delete-indicator [this id])
  (list-indicators [this filtermap params])
  (list-indicators-by-judgements [this judgements params]))

(defprotocol IExploitTargetStore
  (read-exploit-target [this id])
  (create-exploit-targets [this new-exploit-targets])
  (update-exploit-target [this id exploit-target])
  (delete-exploit-target [this id])
  (list-exploit-targets [this filtermap params]))

(defprotocol IFeedbackStore
  (read-feedback [this id])
  (create-feedbacks [this new-feedbacks])
  (delete-feedback [this id])
  (list-feedback [this filtermap params]))

(defprotocol ITTPStore
  (read-ttp [this id])
  (create-ttps [this new-ttps])
  (update-ttp [this id ttp])
  (delete-ttp [this id])
  (list-ttps [this filtermap params]))

(defprotocol ICampaignStore
  (read-campaign [this id])
  (create-campaigns [this new-campaigns])
  (update-campaign [this id campaign])
  (delete-campaign [this id])
  (list-campaigns [this filtermap params]))

(defprotocol ICOAStore
  (read-coa [this id])
  (create-coas [this new-coas])
  (update-coa [this id coa])
  (delete-coa [this id])
  (list-coas [this filtermap params]))

(defprotocol ISightingStore
  (read-sighting [this id])
  (create-sightings [this new-sightings])
  (update-sighting [this id sighting])
  (delete-sighting [this id])
  (list-sightings [this filtermap params])
  (list-sightings-by-observables [this observable params]))

(defprotocol IIncidentStore
  (read-incident [this id])
  (create-incidents [this new-incidents])
  (update-incident [this id incident])
  (delete-incident [this id])
  (list-incidents [this filtermap params]))

(defprotocol IBundleStore
  (read-bundle [this id])
  (create-bundles [this new-bundles])
  (delete-bundle [this id]))

(defprotocol IRelationshipStore
  (read-relationship [this id])
  (create-relationships [this new-relations])
  (delete-relationship [this id])
  (list-relationships [this filtermap params]))

(defprotocol IIdentityStore
  (read-identity [this login])
  (create-identity [this new-identity])
  (delete-identity [this org-id role]))

(defprotocol IDataTableStore
  (read-data-table [this login])
  (create-data-tables [this new-data-tables])
  (delete-data-table [this role])
  (list-data-tables [this filtermap params]))

(defprotocol IQueryStringSearchableStore
  (query-string-search [this query filtermap params]))

(defonce stores (atom {:judgement []
                       :indicator []
                       :feedback []
                       :ttp []
                       :campaign []
                       :actor []
                       :coa []
                       :data-table []
                       :exploit-target []
                       :sighting []
                       :incident []
                       :relationship []
                       :identity []
                       :verdict []
                       :bundle []}))

(defn write-store [store write-fn & args]
  (first (doall (map #(apply write-fn % args) (store @stores)))))

(defn read-store [store read-fn & args]
  (apply read-fn (first (get @stores store)) args))

(defn query-string-search-store [store read-fn & args]
  (log/debug "query-string-search-store args: " args)
  (apply read-fn (first (get @stores store)) args))


