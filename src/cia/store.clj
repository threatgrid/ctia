(ns cia.store)

(defprotocol IJudgementStore
  (create-judgement [this new-judgement])
  (read-judgement [this id])
  (delete-judgement [this id])
  (list-judgements-by-observable [this observable])
  (list-judgements-by-indicator [this indicator-id])
  (calculate-verdict
   ;; Returns the current verdict an observable based on stored judgements.
   [this observable]))

(defprotocol IIndicatorStore
  (create-indicator [this new-indicator])
  (read-indicator [this id])
  (delete-indicator [this id])
  (list-indicators [this filtermap]))

(defprotocol IFeedbackStore
  (create-feedback [this feedback])
  (list-feedback [this filtermap]))

(defprotocol ITTPStore
  (read-ttp [this id])
  (create-ttp [this new-ttp])
  (update-ttp [this ttp])
  (delete-ttp [this id])
  (list-ttps [this filtermap]))

(defprotocol ICampaignStore
  (read-campaign [this id])
  (create-campaign [this new-campaign])
  (update-campaign [this campaign])
  (delete-campaign [this id])
  (list-campaigns [this filtermap]))

(defprotocol IActorStore
  (read-actor [this id])
  (create-actor [this new-actor])
  (update-actor [this actor])
  (delete-actor [this id])
  (list-actors [this filtermap]))

(defprotocol ICOAStore
  (read-coa [this id])
  (create-coa [this new-coa])
  (update-coa [this coa])
  (delete-coa [this id])
  (list-coas [this filtermap]))

(defprotocol ISightingStore
  (read-sighting [this id])
  (create-sighting [this new-sighting])
  (update-sighting [this sighting])
  (delete-sighting [this id])
  (list-sightings [this filtermap]))

(defprotocol IIncidentStore
  (read-incident [this id])
  (create-incident [this new-incident])
  (update-incident [this incident])
  (delete-incident [this id])
  (list-incidents [this filtermap]))

(defprotocol IRelationStore
  (read-relation [this id])
  (create-relation [this new-relation])
  (update-relation [this relation])
  (delete-relation [this id])
  (list-relations [this filtermap]))

;; core model
(defonce judgement-store (atom nil))
(defonce indicator-store (atom nil))
(defonce feedback-store (atom nil))


;; threats
(defonce ttp-store (atom nil))
(defonce campaign-store (atom nil))
(defonce actor-store (atom nil))
(defonce coa-store (atom nil))

;; sightings
(defonce sightings-store (atom nil))

;; incidents
(defonce incident-store (atom nil))

;; relations
(defonce relation-store (atom nil))

(defonce stores
  [judgement-store
   indicator-store
   feedback-store
   ttp-store
   campaign-store
   actor-store
   coa-store
   sightings-store
   incident-store
   relation-store])
