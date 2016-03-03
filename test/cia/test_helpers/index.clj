(ns cia.test-helpers.index
  (:require [cia.store :as store]
            [cia.stores.es.index :as es-index]
            [cia.stores.es.mapping :as mapping]
            [cia.stores.es.store :as es-store]
            [cia.test-helpers.core :as h]
            [clojure.java.io :as io]))

(def state-fixture
  (atom nil))

(defn clean-index! []
  (es-index/delete! (:conn @state-fixture)
                    (:index @state-fixture))
  (es-index/create! (:conn @state-fixture)
                    (:index @state-fixture)))

(defn fixture-clean-index [f]
  (clean-index!)
  (f))

(defn init-store-state [f]
  (fn []
    (f @state-fixture)))

(def es-stores
  {store/actor-store          (init-store-state es-store/->ActorStore)
   store/judgement-store      (init-store-state es-store/->JudgementStore)
   store/feedback-store       (init-store-state es-store/->FeedbackStore)
   store/campaign-store       (init-store-state es-store/->CampaignStore)
   store/coa-store            (init-store-state es-store/->COAStore)
   store/exploit-target-store (init-store-state es-store/->ExploitTargetStore)
   store/incident-store       (init-store-state es-store/->IncidentStore)
   store/indicator-store      (init-store-state es-store/->IndicatorStore)
   store/ttp-store            (init-store-state es-store/->TTPStore)})


(def fixture-es-store
  (do (reset! state-fixture (es-index/init-conn))
      (h/fixture-store es-stores)))
