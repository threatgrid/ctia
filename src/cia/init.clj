(ns cia.init
  (:require [cia.auth :as auth]
            [cia.auth.allow-all :as allow-all]
            [cia.auth.threatgrid :as threatgrid]
            [cia.properties :as properties]
            [cia.store :as store]
            [cia.stores.es.store :as es]
            [cia.stores.es.index :as es-index]
            [cia.stores.memory :as mem]))

(defn init-auth-service! []
  (let [auth-service-name (get-in @properties/properties [:auth :service])]
    (case auth-service-name
      "allow-all" (reset! auth/auth-service (allow-all/->AuthService))
      "threatgrid" (reset! auth/auth-service (threatgrid/make-auth-service
                                              (threatgrid/make-whoami-service)))
      (throw (ex-info "Auth service not configured"
                      {:message "Unknown service"
                       :requested-service auth-service-name})))))

(defn init-mem-store! []
  (let [store-impls {store/actor-store mem/->ActorStore
                     store/judgement-store mem/->JudgementStore
                     store/feedback-store mem/->FeedbackStore
                     store/campaign-store mem/->CampaignStore
                     store/coa-store mem/->COAStore
                     store/exploit-target-store mem/->ExploitTargetStore
                     store/incident-store mem/->IncidentStore
                     store/indicator-store mem/->IndicatorStore
                     store/sighting-store mem/->SightingStore
                     store/ttp-store mem/->TTPStore
                     store/identity-store mem/->IdentityStore}]
    (doseq [[store impl-fn] store-impls]
      (reset! store (impl-fn (atom {}))))))

(defn init-es-store! []
  (let [store-state (es-index/init-conn)
        store-impls {store/actor-store es/->ActorStore
                     store/judgement-store es/->JudgementStore
                     store/feedback-store es/->FeedbackStore
                     store/campaign-store es/->CampaignStore
                     store/coa-store es/->COAStore
                     store/exploit-target-store es/->ExploitTargetStore
                     store/incident-store es/->IncidentStore
                     store/indicator-store es/->IndicatorStore
                     store/ttp-store es/->TTPStore
                     store/identity-store es/->IdentityStore}]

    (es-index/create! (:conn store-state)
                      (:index store-state))

    (doseq [[store impl-fn] store-impls]
      (reset! store (impl-fn store-state)))))

(defn init! []
  (properties/init!)
  (init-auth-service!)
  (init-mem-store!))
