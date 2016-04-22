(ns ctia.init
  (:require [cider.nrepl :refer [cider-nrepl-handler]]
            [clojure.tools.nrepl.server :as nrepl-server]
            [ctia.auth :as auth]
            [ctia.auth.allow-all :as allow-all]
            [ctia.auth.threatgrid :as threatgrid]
            [ctia.events.producer :as producer]
            [ctia.events.producers.es.producer :as es-producer]
            [ctia.http.server :as http-server]
            [ctia.lib.es.index :as es-index]
            [ctia.properties :as p]
            [ctia.store :as store]
            [ctia.stores.atom.store :as as]
            [ctia.stores.es.store :as es-store]
            [ctia.stores.sql.store :as ss]
            [ctia.stores.sql.db :as sql-db]
            [ctia.stores.sql.judgement :as sql-judgement]
            [ctia.flows.autoload :refer [autoload-hooks!]]
            [ctia.flows.hooks :as h]
            [ctia.publish :as pub]
            [ctia.events :as e]
            [ring.adapter.jetty :as jetty]))

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
  (let [store-state (es-store/init-store-conn)
        store-impls {store/actor-store es-store/->ActorStore
                     store/judgement-store es-store/->JudgementStore
                     store/feedback-store es-store/->FeedbackStore
                     store/campaign-store es-store/->CampaignStore
                     store/coa-store es-store/->COAStore
                     store/exploit-target-store es-store/->ExploitTargetStore
                     store/incident-store es-store/->IncidentStore
                     store/indicator-store es-store/->IndicatorStore
                     store/ttp-store es-store/->TTPStore
                     store/sighting-store es-store/->SightingStore
                     store/identity-store es-store/->IdentityStore}]

    (es-index/create! (:conn store-state)
                      (:index store-state)
                      (:mapping store-state))

    (doseq [[store impl-fn] store-impls]
      (reset! store (impl-fn store-state)))))

(defn init-es-producer! []
  (let [producer-state (es-producer/init-producer-conn)]
    (reset! producer/event-producers
            [(es-producer/->EventProducer producer-state)])))

(defn init-store-service! []
  (let [store-service-default (get-in @p/properties [:ctia :store :type])]
    (case store-service-default
      :es (init-es-store!)
      :memory (init-mem-store!)
      ;; SQL store is not a complete store
      :sql (init-sql-store!)
      (throw (ex-info "Store service not configured"
                      {:message "Unknown service"
                       :requested-service store-service-default})))))
(defn init-hooks!
  "Load all the hooks, init them and assure to
  call `destroy` on all hooks when shutting down."
  []
  ;; this is breaking everything
  ;;(autoload-hooks!)
  (h/init-hooks!)
  (h/add-destroy-hooks-hook-at-shutdown))

(defn init-producer-service! []
  (let [producer-service-default (get-in @p/properties [:ctia :producer :type])]
    (case producer-service-default
      :es (init-es-producer!)
      nil nil)))

(defn start-ctia!
  "Does the heavy lifting for ctia.main (ie entry point that isn't a class)"
  [& {:keys [join? silent?]}]

  ;; Configure everything
  (p/init!)
  (e/init!)
  (init-auth-service!)
  (init-store-service!)
  (init-producer-service!)
  (init-hooks!)

  ;; Start nREPL server
  (let [{nrepl-port :port
         nrepl-enabled? :enabled} (get-in @p/properties [:ctia :nrepl])]
    (when (and nrepl-enabled? nrepl-port)
      (when-not silent?
        (println (str "Starting nREPL server on port " nrepl-port)))
      (nrepl-server/start-server :port nrepl-port
                                 :handler cider-nrepl-handler)))
  ;; Start HTTP server
  (let [http-port (get-in @p/properties [:ctia :http :port])]
    (when-not silent?
      (println (str "Starting HTTP server on port " http-port)))
    (http-server/start! :join? join?)))
