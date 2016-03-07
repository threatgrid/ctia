(ns cia.init
  (:require [cia.store :as store]
            [cia.stores.memory :as mem]
            [cia.stores.es.store :as es]
            [cia.stores.es.index :as es-index]))

(defn init-mem-store []
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

(defn init-es-store []
  (let [store-state (es-index/init-conn)
        store-impls {store/actor-store es/->ActorStore
                     store/judgement-store es/->JudgementStore
                     store/feedback-store es/->FeedbackStore
                     store/campaign-store es/->CampaignStore
                     store/coa-store es/->COAStore
                     store/exploit-target-store es/->ExploitTargetStore
                     store/incident-store es/->IncidentStore
                     store/indicator-store es/->IndicatorStore
                     store/ttp-store es/->TTPStore}]

    (es-index/create! (:conn store-state)
                      (:index store-state))

    (doseq [[store impl-fn] store-impls]
      (reset! store (impl-fn store-state)))))


(defn init-store []
  (init-mem-store)
  ;;(init-es-store)
  )
