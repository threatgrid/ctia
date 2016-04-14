(ns ctia.init
  (:require [ctia.auth :as auth]
            [ctia.auth.allow-all :as allow-all]
            [ctia.auth.threatgrid :as threatgrid]
            [ctia.properties :as p]
            [ctia.store :as store]
            [ctia.stores.es.store :as es]
            [ctia.lib.es.index :as es-index]
            [ctia.stores.atom.store :as as]))

(defn init-auth-service! []
  (let [auth-service-type (get-in @p/properties [:ctia :auth :type])]
    (case auth-service-type
      :allow-all (reset! auth/auth-service (allow-all/->AuthService))
      :threatgrid (reset! auth/auth-service (threatgrid/make-auth-service
                                              (threatgrid/make-whoami-service)))
      (throw (ex-info "Auth service not configured"
                      {:message "Unknown service"
                       :requested-service auth-service-type})))))

(defn init-mem-store! []
  (let [store-impls {store/actor-store     as/->ActorStore
                     store/judgement-store as/->JudgementStore
                     store/feedback-store  as/->FeedbackStore
                     store/campaign-store  as/->CampaignStore
                     store/coa-store       as/->COAStore
                     store/exploit-target-store as/->ExploitTargetStore
                     store/incident-store  as/->IncidentStore
                     store/indicator-store as/->IndicatorStore
                     store/sighting-store  as/->SightingStore
                     store/ttp-store       as/->TTPStore
                     store/identity-store  as/->IdentityStore}]
    (doseq [[store impl-fn] store-impls]
      (reset! store (impl-fn (atom {}))))))

(defn init-es-store! []
  (let [store-state (es-index/init-store-conn)
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
                      (:index store-state)
                      (:mapping store-state))

    (doseq [[store impl-fn] store-impls]
      (reset! store (impl-fn store-state)))))

(defn init-store-service! []
  (let [store-service-default (get-in @p/properties [:ctia :store :type])]
    (case store-service-default
      :es (init-es-store!)
      :memory (init-mem-store!)
      (throw (ex-info "Store service not configured"
                      {:message "Unknown service"
                       :requested-service store-service-default})))))

(defn init! []
  (p/init!)
  (init-auth-service!)
  (init-store-service!))
