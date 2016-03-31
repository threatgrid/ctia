(ns ctia.init
  (:require [ctia.auth :as auth]
            [ctia.auth.allow-all :as allow-all]
            [ctia.auth.threatgrid :as threatgrid]
            [ctia.properties :as properties]
            [ctia.store :as store]
            [ctia.stores.es.store :as es]
            [ctia.stores.es.index :as es-index]
            [ctia.stores.memory.actor :as ma]
            [ctia.stores.memory.campaign :as mca]
            [ctia.stores.memory.coa :as mco]
            [ctia.stores.memory.exploit-target :as me]
            [ctia.stores.memory.feedback :as mf]
            [ctia.stores.memory.identity :as mi]
            [ctia.stores.memory.incident :as mic]
            [ctia.stores.memory.indicator :as min]
            [ctia.stores.memory.judgement :as mj]
            [ctia.stores.memory.sighting :as ms]
            [ctia.stores.memory.ttp :as mt]))

(defn init-auth-service! []
  (let [auth-service-name (get-in @properties/properties [:auth :service :name])]
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
