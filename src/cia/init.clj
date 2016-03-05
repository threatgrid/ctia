(ns cia.init
  (:require [cia.properties :as properties]
            [cia.store :as store]
            [cia.stores.memory :as mem]))

(defn init-store []
  (properties/init!)
  (let [store-impls {store/actor-store mem/->ActorStore
                     store/judgement-store mem/->JudgementStore
                     store/feedback-store mem/->FeedbackStore
                     store/campaign-store mem/->CampaignStore
                     store/coa-store mem/->COAStore
                     store/exploit-target-store mem/->ExploitTargetStore
                     store/incident-store mem/->IncidentStore
                     store/indicator-store mem/->IndicatorStore
                     store/ttp-store mem/->TTPStore
                     store/auth-role-store mem/->AuthRoleStore}]
    (doseq [[store impl-fn] store-impls]
      (reset! store (impl-fn (atom {}))))))
