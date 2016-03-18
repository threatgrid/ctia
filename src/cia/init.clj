(ns cia.init
  (:require [cia.auth :as auth]
            [cia.auth.allow-all :as allow-all]
            [cia.auth.threatgrid :as threatgrid]
            [cia.properties :as properties]
            [cia.store :as store]
            [cia.stores.es.store :as es]
            [cia.stores.es.index :as es-index]
            [cia.stores.memory.actor :as ma]
            [cia.stores.memory.campaign :as mca]
            [cia.stores.memory.coa :as mco]
            [cia.stores.memory.exploit-target :as me]
            [cia.stores.memory.feedback :as mf]
            [cia.stores.memory.identity :as mi]
            [cia.stores.memory.incident :as mic]
            [cia.stores.memory.indicator :as min]
            [cia.stores.memory.judgement :as mj]
            [cia.stores.memory.sighting :as ms]
            [cia.stores.memory.ttp :as mt]))

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
  (let [store-impls {store/actor-store     ma/->ActorStore
                     store/judgement-store mj/->JudgementStore
                     store/feedback-store  mf/->FeedbackStore
                     store/campaign-store  mca/->CampaignStore
                     store/coa-store       mco/->COAStore
                     store/exploit-target-store me/->ExploitTargetStore
                     store/incident-store  mic/->IncidentStore
                     store/indicator-store min/->IndicatorStore
                     store/sighting-store  ms/->SightingStore
                     store/ttp-store       mt/->TTPStore
                     store/identity-store  mi/->IdentityStore}]
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
                     store/sighting-store es/->SightingStore
                     store/identity-store es/->IdentityStore}]

    (es-index/create! (:conn store-state)
                      (:index store-state))

    (doseq [[store impl-fn] store-impls]
      (reset! store (impl-fn store-state)))))

(defn init! []
  (properties/init!)
  (init-auth-service!)
  (init-mem-store!))
