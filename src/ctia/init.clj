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

(defn- get-store-types [store-kw get-in-config]
  (or (some-> (get-in-config [:ctia :store store-kw])
              (str/split #","))
      []))

(defn- build-store [store-kw get-in-config store-type]
  (case store-type
    "es" (es-init/init-store! store-kw
                              {:ConfigService {:get-in-config get-in-config}})))

(defn init-store-service! [get-in-config]
  (reset! store/stores
          (->> (keys store/empty-stores)
               (map (fn [store-kw]
                      [store-kw (keep (partial build-store store-kw get-in-config)
                                      (get-store-types store-kw get-in-config))]))
               (into {})
               (merge-with into store/empty-stores))))

(defn log-properties
  [config]
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
  (let [{auth-service-type :type} (get-in config [:ctia :auth])
        auth-svc (case auth-service-type
                   :allow-all allow-all/allow-all-auth-service
                   :threatgrid threatgrid/threatgrid-auth-service
                   :static static-auth/static-auth-service
                   (throw (ex-info "Auth service not configured"
                                   {:message "Unknown service"
                                    :requested-service auth-service-type})))

        {encryption-service-type :type} (get-in config [:ctia :encryption])
        encryption-svc (case encryption-service-type
                         :default encryption-default/default-encryption-service
                         (throw (ex-info "Encryption service not configured"
                                         {:message "Unknown service"
                                          :requested-service encryption-service-type})))]
    [auth-svc
     encryption-svc]))


(defn start-ctia!*
  "Lower-level Trapperkeeper booting function for
  custom services and config."
  [{:keys [services config]}]
  (validate-entities)
  (log-properties config)
  (tk/boot-services-with-config services config))

(defn start-ctia!
  "Does the heavy lifting for ctia.main (ie entry point that isn't a class).
  Returns the Trapperkeeper app."
 ([] (start-ctia! (p/build-init-config)))
 ([config]
  (log/info "starting CTIA version: "
            (version/current-version))

  ;; shutdown hook
  (shutdown/init!)

  ;; events init
  (e/init!)

  ;; metrics reporters init
  (riemann/init! (partial get-in config))
  (jmx/init! (partial get-in config))
  (console/init! (partial get-in config))

  ;; register event file logging only when enabled
  (when (get-in config [:ctia :events :log])
    (event-logging/init!))

  (init-store-service! (partial get-in config))

  ;; hooks init
  (h/init! (partial get-in config))

  (let [services (default-services config)
        app (start-ctia!* {:config config
                           :services services})

        ;; temporary hack for global services
        _ (reset! auth/auth-service (app/get-service app :IAuth))
        _ (reset! encryption/encryption-service (app/get-service app :IEncryption))

        ;; temporary shutdown hook for tests
        ;; Note: this will trigger double-shutdown of Trapperkeeper services on System/exit,
        ;;       so `stop` for our services should be idempotent
        {{:keys [request-shutdown
                 wait-for-shutdown]} :ShutdownService} (app/service-graph app)
        _ (shutdown/register-hook! ::tk-app #(do (reset! auth/auth-service nil)
                                                 (reset! encryption/encryption-service nil)
                                                 (request-shutdown)
                                                 (wait-for-shutdown)))
        ;; Start HTTP server
        ;; Note: temporarily starting server here because it depends on IAuth+IEncryption services
        ;;       which are started by trapperkeeper above. eventually all
        ;;       initialization will be managed by trapperkeeper
        _ (let [{http-port :port
                 enabled? :enabled} (get-in config [:ctia :http])]
            (when enabled?
              (log/info (str "Starting HTTP server on port " http-port))
              (http-server/start! config)))]
    app)))
