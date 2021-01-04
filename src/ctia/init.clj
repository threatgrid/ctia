(ns ctia.init
  (:require
   [ctia.encryption.default :as encryption-default]
   [clj-momo.properties :as mp]
   [clojure.tools.logging :as log]
   [ctia.lib.metrics
    [riemann :as riemann]
    [jmx :as jmx]
    [console :as console]]
   [ctia.lib.utils :as utils]
   [ctia
    [events-service :as events-svc]
    [features-service :as features-svc]
    [logging :as event-logging]
    [properties :as p]
    [store-service :as store-svc]]
   [ctia.auth
    [allow-all :as allow-all]
    [static :as static-auth]
    [threatgrid :as threatgrid]]
   [ctia.ductile-service :as ductile-svc]
   [ctia.version :as version]
   [ctia.graphql-named-type-registry-service :as graphql-registry-svc]
   [ctia.flows.hooks-service :as hooks-svc]
   [ctia.http.server-service :as http-server-svc]
   [puppetlabs.trapperkeeper.app :as app]
   [puppetlabs.trapperkeeper.core :as tk]
   [schema.core :as s]))

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

(s/defn default-services-map
  :- {(s/constrained s/Keyword simple-keyword?)
      ;; could be (s/protocol puppetlabs.trapperkeeper.services/ServiceDefinition),
      ;; but trapperkeeper claims the protocol is internal only
      (s/pred some?)}
  "Returns the default map of CTIA services based on provided properties.
  Keys are the unqualified keyword names of each service protocol, values are
  the defservice instances."
  [config]
  (let [{auth-service-type :type} (get-in config [:ctia :auth])
        auth-svc
        (case auth-service-type
          :allow-all {:IAuth allow-all/allow-all-auth-service}
          :threatgrid {:IAuth threatgrid/threatgrid-auth-service
                       :ThreatgridAuthWhoAmIURLService threatgrid/threatgrid-auth-whoami-url-service}
          :static {:IAuth static-auth/static-auth-service}
          (throw (ex-info "Auth service not configured"
                          {:message "Unknown service"
                           :requested-service auth-service-type})))
        encryption-svc
        (let [{:keys [type] :as encryption-properties}
              (get-in config [:ctia :encryption])]
          (case type
            :default {:IEncryption encryption-default/default-encryption-service}
            (throw (ex-info "Encryption service not configured"
                            {:message "Unknown service"
                             :requested-service type}))))]
    (merge-with
      (fn [l r]
        (throw (ex-info "Service graph conflict!" {:left l :right r})))
      auth-svc
      encryption-svc
      {:EventsService events-svc/events-service
       :StoreService store-svc/store-service
       ;; StoreService dependency
       :DuctileService ductile-svc/ductile-service
       :CTIAHTTPServerService http-server-svc/ctia-http-server-service
       :HooksService hooks-svc/hooks-service
       :GraphQLNamedTypeRegistryService graphql-registry-svc/graphql-named-type-registry-service
       :RiemannMetricsService riemann/riemann-metrics-service
       :JMXMetricsService jmx/jmx-metrics-service
       :ConsoleMetricsService console/console-metrics-service
       :FeaturesService features-svc/features-service}
      ;; register event file logging only when enabled
      (when (get-in config [:ctia :events :log])
        {:EventLoggingService event-logging/event-logging-service}))))

(defn start-ctia!*
  "Lower-level Trapperkeeper booting function for
  custom services and config."
  [{:keys [services config]}]
  (log-properties config)
  (-> (tk/boot-services-with-config services config)
      app/check-for-errors!))

(defn start-ctia!
  "Does the heavy lifting for ctia.main (ie entry point that isn't a class).
  Returns the Trapperkeeper app."
  ([] (start-ctia! {}))
  ([{:keys [services config]}]
   (log/info "starting CTIA version: "
             (version/current-version))

   ;; trapperkeeper init
   (let [config (or config
                    (p/build-init-config))]
     (start-ctia!* {:services (or services
                                  (vals (default-services-map config)))
                    :config config}))))
