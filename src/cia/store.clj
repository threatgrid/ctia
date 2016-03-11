(ns cia.store)

(defprotocol IActorStore
  (read-actor [this id])
  (create-actor [this login new-actor])
  (update-actor [this id login actor])
  (delete-actor [this id])
  (list-actors [this filtermap]))

(defprotocol IJudgementStore
  (create-judgement [this login new-judgement])
  (read-judgement [this id])
  (delete-judgement [this id])
  (list-judgements [this filter-map])
  (calculate-verdict
    ;; Returns the current verdict an observable based on stored judgements.
    [this observable])
  (list-judgements-by-observable [this observable])
  (add-indicator-to-judgement [this judgement-id indicator-relationship]))

(defprotocol IIndicatorStore
  (create-indicator [this login new-indicator])
  (update-indicator [this id login indicator])
  (read-indicator [this id])
  (delete-indicator [this id])
  (list-indicators [this filtermap])
  (list-indicators-by-judgements [this judgements]))

(defprotocol IExploitTargetStore
  (read-exploit-target [this id])
  (create-exploit-target [this login new-exploit-target])
  (update-exploit-target [this id login exploit-target])
  (delete-exploit-target [this id])
  (list-exploit-targets [this filtermap]))

(defprotocol IFeedbackStore
  (create-feedback [this new-feedback login judgement-id])
  (list-feedback [this filtermap]))

(defprotocol ITTPStore
  (read-ttp [this id])
  (create-ttp [this login new-ttp])
  (update-ttp [this id login ttp])
  (delete-ttp [this id])
  (list-ttps [this filtermap]))

(defprotocol ICampaignStore
  (read-campaign [this id])
  (create-campaign [this login new-campaign])
  (update-campaign [this id login campaign])
  (delete-campaign [this id])
  (list-campaigns [this filtermap]))

(defprotocol ICOAStore
  (read-coa [this id])
  (create-coa [this login new-coa])
  (update-coa [this id login coa])
  (delete-coa [this id])
  (list-coas [this filtermap]))

(defprotocol ISightingStore
  (read-sighting [this id])
  (create-sighting [this login new-sighting])
  (update-sighting [this id login sighting])
  (delete-sighting [this id])
  (list-sightings [this filtermap])
  (list-sightings-by-indicators [this indicators]))

(defprotocol IIncidentStore
  (read-incident [this id])
  (create-incident [this login new-incident])
  (update-incident [this id login incident])
  (delete-incident [this id])
  (list-incidents [this filtermap]))

(defprotocol IRelationStore
  (read-relation [this id])
  (create-relation [this login new-relation])
  (update-relation [this login relation])
  (delete-relation [this id])
  (list-relations [this filtermap]))

(defprotocol IIdentityStore
  (read-identity [this login])
  (create-identity [this new-identity])
  (delete-identity [this org-id role]))

;; core model
(defonce judgement-store (atom nil))
(defonce indicator-store (atom nil))
(defonce feedback-store (atom nil))

;; threats
(defonce ttp-store (atom nil))
(defonce campaign-store (atom nil))
(defonce actor-store (atom nil))
(defonce coa-store (atom nil))
(defonce exploit-target-store (atom nil))

;; sightings
(defonce sighting-store (atom nil))

;; incidents
(defonce incident-store (atom nil))

;; relations
(defonce relation-store (atom nil))

;; internal
(defonce identity-store (atom nil))
