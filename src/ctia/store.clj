(ns ctia.store)

(defprotocol IActorStore
  (read-actor [this id])
  (create-actor [this new-actor])
  (update-actor [this id actor])
  (delete-actor [this id])
  (list-actors [this filtermap params]))

(defprotocol IJudgementStore
  (create-judgement [this new-judgement])
  (read-judgement [this id])
  (delete-judgement [this id])
  (list-judgements [this filter-map params])
  (calculate-verdict [this observable])
  (list-judgements-by-observable [this observable params])
  (add-indicator-to-judgement [this judgement-id indicator-relationship]))

(defprotocol IIndicatorStore
  (create-indicator [this new-indicator])
  (update-indicator [this id indicator])
  (read-indicator [this id])
  (delete-indicator [this id])
  (list-indicators [this filtermap params])
  (list-indicators-by-judgements [this judgements params]))

(defprotocol IExploitTargetStore
  (read-exploit-target [this id])
  (create-exploit-target [this new-exploit-target])
  (update-exploit-target [this id exploit-target])
  (delete-exploit-target [this id])
  (list-exploit-targets [this filtermap params]))

(defprotocol IFeedbackStore
  (read-feedback [this id])
  (create-feedback [this new-feedback])
  (delete-feedback [this id])
  (list-feedback [this filtermap params]))

(defprotocol ITTPStore
  (read-ttp [this id])
  (create-ttp [this new-ttp])
  (update-ttp [this id ttp])
  (delete-ttp [this id])
  (list-ttps [this filtermap params]))

(defprotocol ICampaignStore
  (read-campaign [this id])
  (create-campaign [this new-campaign])
  (update-campaign [this id campaign])
  (delete-campaign [this id])
  (list-campaigns [this filtermap params]))

(defprotocol ICOAStore
  (read-coa [this id])
  (create-coa [this new-coa])
  (update-coa [this id coa])
  (delete-coa [this id])
  (list-coas [this filtermap params]))

(defprotocol ISightingStore
  (read-sighting [this id])
  (create-sighting [this new-sighting])
  (update-sighting [this id sighting])
  (delete-sighting [this id])
  (list-sightings [this filtermap params])
  (list-sightings-by-indicators [this indicators params])
  (list-sightings-by-observables [this observable params]))

(defprotocol IIncidentStore
  (read-incident [this id])
  (create-incident [this new-incident])
  (update-incident [this id incident])
  (delete-incident [this id])
  (list-incidents [this filtermap params]))

(defprotocol IRelationStore
  (read-relation [this id])
  (create-relation [this new-relation])
  (update-relation [this relation])
  (delete-relation [this id])
  (list-relations [this filtermap params]))

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

(def stores
  {:judgement judgement-store
   :indicator indicator-store
   :feedback feedback-store
   :ttp ttp-store
   :campaign campaign-store
   :actor actor-store
   :coa coa-store
   :exploit-target exploit-target-store
   :sighting sighting-store
   :incident incident-store
   ;;:relation relation-store
   :identity identity-store})
