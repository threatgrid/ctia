(ns ctia.init
  (:require [cider.nrepl :refer [cider-nrepl-handler]]
            [clj-momo.properties :as mp]
            [clojure.tools.nrepl.server :as nrepl-server]
            [clojure.tools.logging :as log]
            [ctia.lib.metrics
             [riemann :as riemann]
             [jmx :as jmx]
             [console :as console]]
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
            [ctia.shutdown :as shutdown]
            [ctia.stores.atom.store :as as-store]
            [ctia.stores.es.store :as es-store]))

(defn init-auth-service! []
  (let [auth-service-type (get-in @p/properties [:ctia :auth :type])]
    (case auth-service-type
      :allow-all (reset! auth/auth-service (allow-all/->AuthService))
      :threatgrid (reset! auth/auth-service (threatgrid/make-auth-service))
      (throw (ex-info "Auth service not configured"
                      {:message "Unknown service"
                       :requested-service auth-service-type})))))

(def store-factories
  { ;; A :builder is called on each store creation and is passed the
   ;; associated factory function.  It should return the factory
   ;; result.  This is where you can do idempotent store setup.
   ;; Specifying a :builder is optional.
   :builder
   {:atom (fn atom-builder [factory props]
            (factory (as-store/init! props)))
    :es (fn es-builder [factory props]
          (factory (es-store/init! props)))}

   :actor
   {:atom as-store/->ActorStore
    :es es-store/->ActorStore}

   :campaign
   {:atom as-store/->CampaignStore
    :es es-store/->CampaignStore}

   :coa
   {:atom as-store/->COAStore
    :es es-store/->COAStore}

   :data-table
   {:atom as-store/->DataTableStore
    :es es-store/->DataTableStore}

   :exploit-target
   {:atom as-store/->ExploitTargetStore
    :es es-store/->ExploitTargetStore}

   :feedback
   {:atom as-store/->FeedbackStore
    :es es-store/->FeedbackStore}

   :identity
   {:atom as-store/->IdentityStore
    :es es-store/->IdentityStore}

   :incident
   {:atom as-store/->IncidentStore
    :es es-store/->IncidentStore}

   :indicator
   {:atom as-store/->IndicatorStore
    :es es-store/->IndicatorStore}

   :judgement
   {:atom as-store/->JudgementStore
    :es es-store/->JudgementStore}

   :relationship
   {:atom as-store/->RelationshipStore
    :es es-store/->RelationshipStore}

   :verdict
   {:atom as-store/->VerdictStore
    :es es-store/->VerdictStore}

   :sighting
   {:atom as-store/->SightingStore
    :es es-store/->SightingStore}

   :ttp
   {:atom as-store/->TTPStore
    :es es-store/->TTPStore}

   :bundle
   {:atom as-store/->BundleStore
    :es es-store/->BundleStore}})

(defn init-store-service! []
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
                   (clojure.pprint/pprint
                    (mp/debug-properties-by-source p/PropertiesSchema
                                                   p/files)))))

  (log/info (with-out-str
              (do (newline)
                  (clojure.pprint/pprint @p/properties)))))
(defn start-ctia!
  "Does the heavy lifting for ctia.main (ie entry point that isn't a class)"
  [& {:keys [join?]}]

  (log/info "starting CTIA version: "
            (version/current-version))

  ;; shutdown hook
  (shutdown/init!)

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
