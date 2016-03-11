(ns cia.test-helpers.index
  "ES Index test helpers"
  (:require [cia.store :as store]
            [cia.stores.es.index :as es-index]
            [cia.stores.es.mapping :as mapping]
            [cia.stores.es.store :as es-store]
            [cia.test-helpers.core :as h]
            [cia.properties :as properties]
            [clojure.java.io :as io]))

(def conn-state-fixture
  "for testing the same es conn for all ES Stores"
  (atom nil))

(defn clean-index! []
  "delete and recreate the index"
  (es-index/delete! (:conn @conn-state-fixture)
                    (:index @conn-state-fixture))
  (es-index/create! (:conn @conn-state-fixture)
                    (:index @conn-state-fixture)))

(defn fixture-clean-index [f]
  (clean-index!)
  (f))

(defn init-store-state [f]
  "spwan the ES stores
   with a conn and index name as state"
  (fn []
    (f @conn-state-fixture)))

(def es-stores
  {store/actor-store          (init-store-state es-store/->ActorStore)
   store/judgement-store      (init-store-state es-store/->JudgementStore)
   store/feedback-store       (init-store-state es-store/->FeedbackStore)
   store/campaign-store       (init-store-state es-store/->CampaignStore)
   store/coa-store            (init-store-state es-store/->COAStore)
   store/exploit-target-store (init-store-state es-store/->ExploitTargetStore)
   store/incident-store       (init-store-state es-store/->IncidentStore)
   store/indicator-store      (init-store-state es-store/->IndicatorStore)
   store/ttp-store            (init-store-state es-store/->TTPStore)
   store/sighting-store       (init-store-state es-store/->SightingStore)
   store/identity-store       (init-store-state es-store/->IdentityStore)})

(def fixture-es-store
  (do
    (properties/init!)
    (reset! conn-state-fixture
            (es-index/init-conn))
    (h/fixture-store es-stores)))
