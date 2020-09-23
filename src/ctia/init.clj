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
    [store :as store]
    [store-service :as store-svc]
    [store-service-core :as store-svc-core]]
   [ctia.auth
    [allow-all :as allow-all]
    [static :as static-auth]
    [threatgrid :as threatgrid]]
   [ctia.version :as version]
   [ctia.graphql-service :as graphql-svc]
   [ctia.graphql-named-type-registry-service :as graphql-registry-svc]
   [ctia.flows.hooks-service :as hooks-svc]
   [ctia.http.server-service :as http-server-svc]
   [ctia.stores.es.init :as es-init]
   [puppetlabs.trapperkeeper.core :as tk]))

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
        auth-svc
        (case auth-service-type
          :allow-all allow-all/allow-all-auth-service
          :threatgrid threatgrid/threatgrid-auth-service
          :static static-auth/static-auth-service
          (throw (ex-info "Auth service not configured"
                          {:message "Unknown service"
                           :requested-service auth-service-type})))
        encryption-svc
        (let [{:keys [type] :as encryption-properties}
              (get-in config [:ctia :encryption])]
          (case type
            :default encryption-default/default-encryption-service
            (throw (ex-info "Encryption service not configured"
                            {:message "Unknown service"
                             :requested-service type}))))]
    (into
      [auth-svc
       encryption-svc
       e/events-service
       store-svc/store-service
       http-server-svc/ctia-http-server-service
       hooks-svc/hooks-service
       graphql-svc/graphql-service
       graphql-registry-svc/graphql-named-type-registry-service
       riemann/riemann-metrics-service
       jmx/jmx-metrics-service
       console/console-metrics-service]
      ;; register event file logging only when enabled
      (when (get-in config [:ctia :events :log])
        [event-logging/event-logging-service]))))

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
  []
  (log/info "starting CTIA version: "
            (version/current-version))

  ;; trapperkeeper init
  (let [config (p/build-init-config)]
    (start-ctia!* {:services (default-services config)
                   :config config})))
