(ns cia.init
  (:require [cia.auth :as auth]
            [cia.auth.allow-all :as allow-all]
            [cia.auth.threatgrid :as threatgrid]
            [cia.properties :as properties]
            [cia.store :as store]
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

(defn init-store! []
  (let [store-impls {store/actor-store mem/->ActorStore
                     store/judgement-store mem/->JudgementStore
                     store/feedback-store mem/->FeedbackStore
                     store/campaign-store mem/->CampaignStore
                     store/coa-store mem/->COAStore
                     store/exploit-target-store mem/->ExploitTargetStore
                     store/incident-store mem/->IncidentStore
                     store/indicator-store mem/->IndicatorStore
                     store/ttp-store mem/->TTPStore
                     store/identity-store mem/->IdentityStore}]
    (doseq [[store impl-fn] store-impls]
      (reset! store (impl-fn (atom {}))))))

(defn init! []
  (properties/init!)
  (init-auth-service!)
  (init-store!))
