(ns cia.init
  (:require [cia.store :as store]
            [cia.stores.memory :as mem]))

(defn init-store []
  (let [store-impls {store/actor-store mem/->ActorStore
                     store/judgement-store mem/->JudgementStore
                     store/feedback-store mem/->FeedbackStore
                     store/campaign-store mem/->CampaignStore
                     store/coa-store mem/->COAStore
                     store/exploit-target-store mem/->ExploitTargetStore
                     store/incident-store mem/->IncidentStore
                     store/indicator-store mem/->IndicatorStore
                     store/ttp-store mem/->TTPStore}]
    (doseq [[store impl-fn] store-impls]
      (reset! store (impl-fn (atom {}))))))
