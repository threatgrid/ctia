(ns ctia.init
  (:require [ctia.auth :as auth]
            [ctia.auth.allow-all :as allow-all]
            [ctia.auth.threatgrid :as threatgrid]
            [ctia.properties :as p]
            [ctia.store :as store]
            [ctia.stores.es.store :as es]
            [ctia.lib.es.index :as es-index]
            [ctia.stores.atom.store :as as]
            [ctia.stores.sql.store :as ss]
            [ctia.stores.sql.db :as sql-db]
            [ctia.stores.sql.judgement :as sql-judgement]))

(defn init-auth-service! []
  (let [auth-service-type (get-in @p/properties [:ctia :auth :type])]
    (case auth-service-type
      :allow-all (reset! auth/auth-service (allow-all/->AuthService))
      :threatgrid (reset! auth/auth-service (threatgrid/make-auth-service))
      (throw (ex-info "Auth service not configured"
                      {:message "Unknown service"
                       :requested-service auth-service-type})))))

(defn atom-init [factory]
  (fn []
    (factory (atom {}))))

(def atom-store-factories
  {store/actor-store     (atom-init as/->ActorStore)
   store/judgement-store (atom-init as/->JudgementStore)
   store/feedback-store  (atom-init as/->FeedbackStore)
   store/campaign-store  (atom-init as/->CampaignStore)
   store/coa-store       (atom-init as/->COAStore)
   store/exploit-target-store (atom-init as/->ExploitTargetStore)
   store/incident-store  (atom-init as/->IncidentStore)
   store/indicator-store (atom-init as/->IndicatorStore)
   store/sighting-store  (atom-init as/->SightingStore)
   store/ttp-store       (atom-init as/->TTPStore)
   store/identity-store  (atom-init as/->IdentityStore)})

(defn init-mem-store! []
  (doseq [[store impl-fn] atom-store-factories]
    (reset! store (impl-fn))))

(defn init-sql-store! []
  (sql-db/init!)
  (sql-judgement/init!)
  (doseq [[store impl-fn] (merge atom-store-factories
                                 {store/judgement-store ss/->JudgementStore})]
    (reset! store (impl-fn))))

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
      :sql (init-sql-store!)
      (throw (ex-info "Store service not configured"
                      {:message "Unknown service"
                       :requested-service store-service-default})))))

(defn init! []
  (p/init!)
  (init-auth-service!)
  (init-store-service!))
