(ns ctia.init
  (:require [cider.nrepl :refer [cider-nrepl-handler]]
            [clojure.tools.nrepl.server :as nrepl-server]
            [clojure.tools.logging :as log]
            [ctia.http.middleware.metrics.riemann :as riemann]
            [ctia.http.middleware.metrics.jmx :as jmx]
            [ctia.http.middleware.metrics.console :as console]
            [ctia
             [auth :as auth]
             [events :as e]
             [logging :as event-logging]
             [properties :as p]
             [store :as store]]
            [ctia.auth
             [allow-all :as allow-all]
             [threatgrid :as threatgrid]]
            [ctia.version :as version]
            [ctia.flows.hooks :as h]
            [ctia.http.server :as http-server]
            [ctia.stores.atom.store :as as]
            [ctia.stores.es.store :as es-store]
            [ctia.stores.sql
             [db :as sql-store]
             [judgement :as sql-judgement]
             [store :as ss]]))

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
  (sql-store/shutdown!))

(def store-factories
  {;; A :builder is called on each store creation and is passed the
   ;; associated factory function.  It should return the factory
   ;; result.  This is where you can do idempotent store setup.
   ;; Specifying a :builder is optional.
   :builder
   {:atom (fn atom-builder [factory props]
            (factory (atom {})))
    :es (fn es-builder [factory props]
          (factory (es-store/init! props)))
    :sql (fn sql-builder [factory props]
           (sql-store/init! props)
           (factory))}

   :actor
   {:atom as/->ActorStore
    :es es-store/->ActorStore}

   :campaign
   {:atom as/->CampaignStore
    :es es-store/->CampaignStore}

   :coa
   {:atom as/->COAStore
    :es es-store/->COAStore}

   :exploit-target
   {:atom as/->ExploitTargetStore
    :es es-store/->ExploitTargetStore}

   :feedback
   {:atom as/->FeedbackStore
    :es es-store/->FeedbackStore}

   :identity
   {:atom as/->IdentityStore
    :es es-store/->IdentityStore}

   :incident
   {:atom as/->IncidentStore
    :es es-store/->IncidentStore}

   :indicator
   {:atom as/->IndicatorStore
    :es es-store/->IndicatorStore}

   :judgement
   {:atom as/->JudgementStore
    :es es-store/->JudgementStore
    :sql #(do (sql-judgement/init!)
              (ss/->JudgementStore))}

   :verdict
   {:atom as/->VerdictStore
    :es es-store/->VerdictStore}

   :sighting
   {:atom as/->SightingStore
    :es es-store/->SightingStore}

   :ttp
   {:atom as/->TTPStore
    :es es-store/->TTPStore}})

(defn init-store-service! []
  (store-factory-cleaner)
  (doseq [[entity-key impls] @store/stores]
    (swap! store/stores assoc entity-key []))

  (doseq [[store-key store-list] @store/stores]
    (let [store-impls (or (some-> (get-in @p/properties [:ctia :store store-key])
                                  (clojure.string/split #",")) [])
          store-properties (map (fn [impl]
                                  {:properties (merge (get-in @p/properties [:ctia :store impl :default] {})
                                                      (get-in @p/properties [:ctia :store impl store-key] {}))

                                   :builder (get-in store-factories [:builder impl]
                                                    (fn default-builder [f p] (f)))

                                   :factory (get-in store-factories [store-key impl]
                                                    #(throw (ex-info (format "Could not configure %s store" impl)
                                                                     {:store-key store-key
                                                                      :store-type (keyword %)})))}) (map keyword store-impls))

          store-instances (doall (map (fn [{:keys [builder factory properties]}]
                                        (builder factory properties)) store-properties))]

      (swap! store/stores assoc store-key store-instances))))

(defn log-properties []
  (log/debug (with-out-str
               (do (newline)
                   (clojure.pprint/pprint (p/debug-properties-by-source)))))

  (log/info (with-out-str
              (do (newline)
                  (clojure.pprint/pprint @p/properties)))))
(defn start-ctia!
  "Does the heavy lifting for ctia.main (ie entry point that isn't a class)"
  [& {:keys [join?]}]

  (log/info "starting CTIA version: "
            (version/current-version))

  ;; properties init
  (p/init!)

  (log-properties)

  ;; events init
  (e/init!)

  ;; metrics reporters init
  (riemann/init!)
  (jmx/init!)
  (console/init!)

  ;; register event file logging only when enabled
  (when (get-in @p/properties [:ctia :events :log])
    (event-logging/init!))

  (init-auth-service!)
  (init-store-service!)

  ;; hooks init
  (h/init!)

  ;; Start nREPL server
  (let [{nrepl-port :port
         nrepl-enabled? :enabled} (get-in @p/properties [:ctia :nrepl])]
    (when (and nrepl-enabled? nrepl-port)
      (log/info (str "Starting nREPL server on port " nrepl-port))
      (nrepl-server/start-server :port nrepl-port
                                 :handler cider-nrepl-handler)))
  ;; Start HTTP server
  (let [{http-port :port
         enabled? :enabled} (get-in @p/properties [:ctia :http])]
    (when enabled?
      (log/info (str "Starting HTTP server on port " http-port))
      (http-server/start! :join? join?))))
