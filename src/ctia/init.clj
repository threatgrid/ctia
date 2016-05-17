(ns ctia.init
  (:require [cider.nrepl :refer [cider-nrepl-handler]]
            [clojure.core.memoize :as memo]
            [clojure.tools.nrepl.server :as nrepl-server]
            [ctia.auth :as auth]
            [ctia.auth.allow-all :as allow-all]
            [ctia.auth.threatgrid :as threatgrid]
            [ctia.http.server :as http-server]
            [ctia.lib.es.index :as es-index]
            [ctia.properties :as p]
            [ctia.store :as store]
            [ctia.stores.atom.store :as as]
            [ctia.stores.es.store :as es-store]
            [ctia.stores.sql.store :as ss]
            [ctia.stores.sql.db :as sql-store]
            [ctia.stores.sql.judgement :as sql-judgement]
            [ctia.flows.hooks :as h]
            [ctia.logging :as log]
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

(defn store-factory-cleaner
  "The cleaner is called before the stores are instantiated, to reset any state.
   It is unaware of any selected store type, so it should handle all types."
  []
  (es-store/shutdown!)
  (sql-store/shutdown!))

(def store-factories
  {;; A :builder is called on each store creation and is passed the
   ;; associated factory function.  It should return the factory
   ;; result.  This is where you can do idempotent store setup.
   ;; Specifying a :builder is optional.
   :builder
   {:memory (fn memory-builder [factory]
              (factory (atom {})))
    :es (fn es-builder [factory]
          (when (es-store/uninitialized?)
            (es-store/init!))
          (factory @es-store/es-state))
    :sql (fn sql-builder [factory]
           (when (sql-store/uninitialized?)
             (sql-store/init!))
           (factory))}

   :actor
   {:memory as/->ActorStore
    :es es-store/->ActorStore}

   :campaign
   {:memory as/->CampaignStore
    :es es-store/->CampaignStore}

   :coa
   {:memory as/->COAStore
    :es es-store/->COAStore}

   :exploit-target
   {:memory as/->ExploitTargetStore
    :es es-store/->ExploitTargetStore}

   :feedback
   {:memory as/->FeedbackStore
    :es es-store/->FeedbackStore}

   :identity
   {:memory as/->IdentityStore
    :es es-store/->IdentityStore}

   :incident
   {:memory as/->IncidentStore
    :es es-store/->IncidentStore}

   :indicator
   {:memory as/->IndicatorStore
    :es es-store/->IndicatorStore}

   :judgement
   {:memory as/->JudgementStore
    :es es-store/->JudgementStore
    :sql #(do (sql-judgement/init!)
              (ss/->JudgementStore))}

   :sighting
   {:memory as/->SightingStore
    :es es-store/->SightingStore}

   :ttp
   {:memory as/->TTPStore
    :es es-store/->TTPStore}})

(defn init-store-service! []
  (store-factory-cleaner)
  (doseq [[store-type store-atom] store/stores]
    (let [selected-store (get-in @p/properties
                                 [:ctia :store store-type])
          builder (get-in store-factories
                          [:builder selected-store]
                          (fn default-builder [f] (f)))
          factory (get-in store-factories
                          [store-type selected-store]
                          #(throw (ex-info (format "Could not configure %s store"
                                                   store-type)
                                           {:store-type store-type
                                            :selected-store selected-store})))]
      (if (= selected-store :none)
        (reset! store-atom nil)
        (reset! store-atom (builder factory))))))

(defn start-ctia!
  "Does the heavy lifting for ctia.main (ie entry point that isn't a class)"
  [& {:keys [join? silent?]}]

  ;; Configure everything
  (p/init!)
  (e/init!)
  (log/init!)
  (init-auth-service!)
  (init-store-service!)
  (h/init!)

  ;; Start nREPL server
  (let [{nrepl-port :port
         nrepl-enabled? :enabled} (get-in @p/properties [:ctia :nrepl])]
    (when (and nrepl-enabled? nrepl-port)
      (when-not silent?
        (println (str "Starting nREPL server on port " nrepl-port)))
      (nrepl-server/start-server :port nrepl-port
                                 :handler cider-nrepl-handler)))
  ;; Start HTTP server
  (let [{http-port :port
         enabled? :enabled} (get-in @p/properties [:ctia :http])]
    (when enabled?
      (when-not silent?
        (println (str "Starting HTTP server on port " http-port)))
      (http-server/start! :join? join?))))
