(ns ctia.init
  (:require
   [ctia.encryption.default :as encryption-default]
   [ctia.encryption :as encryption]
   [ctia.entity.entities :refer [validate-entities]]
   [clj-momo.properties :as mp]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [ctia.lib.metrics
    [riemann :as riemann]
    [jmx :as jmx]
    [console :as console]]
   [ctia.lib.utils :as utils]
   [ctia
    [auth :as auth]
    [events :as e]
    [logging :as event-logging]
    [properties :as p]
    [store :as store]]
   [ctia.auth
    [allow-all :as allow-all]
    [static :as static-auth]
    [threatgrid :as threatgrid]]
   [ctia.version :as version]
   [ctia.flows.hooks :as h]
   [ctia.http.server :as http-server]
   [ctia.shutdown :as shutdown]
   [ctia.stores.es
    [init :as es-init]]
   [puppetlabs.trapperkeeper.core :as tk]
   [puppetlabs.trapperkeeper.app :as app]))

(defn init-encryption-service! []
  (let [{:keys [type] :as encryption-properties}
        (p/get-in-global-properties [:ctia :encryption])]

    (case type
      :default (do (reset! encryption/encryption-service
                           (encryption-default/map->EncryptionService
                            {:state (atom nil)}))
                   (encryption/init
                    @encryption/encryption-service
                    encryption-properties))
      (throw (ex-info "Encryption service not configured"
                      {:message "Unknown service"
                       :requested-service type})))))

(defn- get-store-types [store-kw]
  (or (some-> (p/get-in-global-properties [:ctia :store store-kw])
              (str/split #","))
      []))

(defn- build-store [store-kw store-type]
  (case store-type
    "es" (es-init/init-store! store-kw
                              {:ConfigService {:get-in-config p/get-in-global-properties}})))

(defn init-store-service! []
  (reset! store/stores
          (->> (keys store/empty-stores)
               (map (fn [store-kw]
                      [store-kw (keep (partial build-store store-kw)
                                      (get-store-types store-kw))]))
               (into {})
               (merge-with into store/empty-stores))))

(defn log-properties [config]
  (log/debug (with-out-str
               (do (newline)
                   (utils/safe-pprint
                    (mp/debug-properties-by-source p/PropertiesSchema
                                                   p/files)))))

  (log/info (with-out-str
              (do (newline)
                  (utils/safe-pprint config)))))

(defn default-services
  "Returns the default collection of CTIA services based on provided properties."
  [config]
  (let [auth-svc
        (let [{auth-service-type :type :as auth} (get-in config [:ctia :auth])]
          (case auth-service-type
            :allow-all allow-all/allow-all-auth-service
            :threatgrid threatgrid/threatgrid-auth-service
            :static static-auth/static-auth-service
            (throw (ex-info "Auth service not configured"
                            {:message "Unknown service"
                             :requested-service auth-service-type}))))]
    [auth-svc]))


(defn start-ctia!*
  "Lower-level Trapperkeeper booting function for
  custom services and config."
  [{:keys [services config]}]
  (let [_ (validate-entities)
        _ (log-properties config)
        app (tk/boot-services-with-config services config)]
    app))

(defn start-ctia!
  "Does the heavy lifting for ctia.main (ie entry point that isn't a class).
  Returns the Trapperkeeper app."
  []
  (log/info "starting CTIA version: "
            (version/current-version))

  ;; shutdown hook
  (shutdown/init!)

  ;; properties init
  (p/init!)

  ;; events init
  (e/init!)

  ;; metrics reporters init
  (riemann/init! p/get-in-global-properties)
  (jmx/init! p/get-in-global-properties)
  (console/init! p/get-in-global-properties)

  ;; register event file logging only when enabled
  (when (p/get-in-global-properties [:ctia :events :log])
    (event-logging/init!))

  (init-encryption-service!)
  (init-store-service!)

  ;; hooks init
  (h/init!)

  (let [config (p/get-global-properties)
        services (default-services config)
        app (start-ctia!* {:config config
                           :services services})

        ;; temporary hack for global IAuth service
        _ (reset! auth/auth-service (app/get-service app :IAuth))

        {{:keys [request-shutdown
                 wait-for-shutdown]} :ShutdownService} (app/service-graph app)
        _
        ;; Start HTTP server
        ;; Note: temporarily starting server here because it depends on IAuth service
        ;;       which is started by trapperkeeper above. eventually all
        ;;       initialization will be managed by trapperkeeper
        (let [{http-port :port
               enabled? :enabled} (p/get-in-global-properties [:ctia :http])]
          (when enabled?
            (log/info (str "Starting HTTP server on port " http-port))
            (http-server/start! :join? false)))]
    ;; temporary shutdown hook for tests
    ;; Note: this will trigger double-shutdown of Trapperkeeper services on System/exit,
    ;;       so `stop` for our services should be idempotent
    (shutdown/register-hook! ::tk-app #(do (request-shutdown)
                                           (wait-for-shutdown)))
    app))
