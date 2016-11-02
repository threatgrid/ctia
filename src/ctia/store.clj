(ns ctia.store)

(defprotocol IActorStore
  (read-actor [this id])
  (create-actor [this new-actor-chan])
  (update-actor [this id actor-chan])
  (delete-actor [this id-chan])
  (list-actors [this filtermap params]))

(defprotocol IJudgementStore
  (create-judgement [this new-judgement-chan])
  (read-judgement [this id])
  (delete-judgement [this id-chan])
  (list-judgements [this filter-map params])
  (calculate-verdict [this observable])
  (list-judgements-by-observable [this observable params])
  (add-indicator-to-judgement [this judgement-id indicator-relationship]))

(defprotocol IVerdictStore
  (create-verdict [this new-verdict-chan])
  (read-verdict [this id])
  (delete-verdict [this id-chan])
  (list-verdicts [this filter-map params]))

(defprotocol IIndicatorStore
  (create-indicator [this new-indicator-chan])
  (update-indicator [this id indicator-chan])
  (read-indicator [this id])
  (delete-indicator [this id-chan])
  (list-indicators [this filtermap params])
  (list-indicators-by-judgements [this judgements params]))

(defprotocol IExploitTargetStore
  (read-exploit-target [this id])
  (create-exploit-target [this new-exploit-target-chan])
  (update-exploit-target [this id exploit-target-chan])
  (delete-exploit-target [this id-chan])
  (list-exploit-targets [this filtermap params]))

(defprotocol IFeedbackStore
  (read-feedback [this id])
  (create-feedback [this new-feedback-chan])
  (delete-feedback [this id-chan])
  (list-feedback [this filtermap params]))

(defprotocol ITTPStore
  (read-ttp [this id])
  (create-ttp [this new-ttp-chan])
  (update-ttp [this id ttp-chan])
  (delete-ttp [this id-chan])
  (list-ttps [this filtermap params]))

(defprotocol ICampaignStore
  (read-campaign [this id])
  (create-campaign [this new-campaign-chan])
  (update-campaign [this id campaign-chan])
  (delete-campaign [this id-chan])
  (list-campaigns [this filtermap params]))

(defprotocol ICOAStore
  (read-coa [this id])
  (create-coa [this new-coa-chan])
  (update-coa [this id coa-chan])
  (delete-coa [this id-chan])
  (list-coas [this filtermap params]))

(defprotocol ISightingStore
  (read-sighting [this id])
  (create-sighting [this new-sighting-chan])
  (update-sighting [this id sighting-chan])
  (delete-sighting [this id-chan])
  (list-sightings [this filtermap params])
  (list-sightings-by-observables [this observable params]))

(defprotocol IIncidentStore
  (read-incident [this id])
  (create-incident [this new-incident-chan])
  (update-incident [this id incident-chan])
  (delete-incident [this id-chan])
  (list-incidents [this filtermap params]))

(defprotocol IBundleStore
  (read-bundle [this id])
  (create-bundle [this new-bundle-chan])
  (delete-bundle [this id-chan]))

(defprotocol IRelationshipStore
  (read-relationship [this id])
  (create-relationship [this new-relation-chan])
  (delete-relationship [this id-chan])
  (list-relationships [this filtermap params]))

(defprotocol IIdentityStore
  (read-identity [this login])
  (create-identity [this new-identity])
  (delete-identity [this org-id role]))

(defprotocol IDataTableStore
  (read-data-table [this login])
  (create-data-table [this new-data-table-chan])
  (delete-data-table [this id-chan])
  (list-data-tables [this filtermap params]))

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

(defn write-store [store-key write-fn & args]
  (first (doall (map (fn apply-write-fn [store]
                       (apply write-fn store args))
                     (get @stores store-key)))))

(defn read-store [store-key read-fn & args]
  (apply read-fn (first (get @stores store-key)) args))
