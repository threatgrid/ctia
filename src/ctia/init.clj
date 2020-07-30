(ns ctia.init
  (:require
   [ctia.encryption.default :as encryption-default]
   [ctia.encryption :as encryption]
   [ctia.entity.entities :refer [validate-entities]]
   [clj-momo.properties :as mp]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   #_
   [ctia.lib.metrics
    #_[riemann :as riemann] ;; never enabled
    #_[jmx :as jmx] ;; never enabled
    #_[console :as console] ;; never enabled
    ]
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
   [ctia.flows.hooks-service :as hooks-svc]
   [ctia.http.server-service :as http-server-svc]
   [ctia.stores.es-service :as es-svc]
   [ctia.stores.es
    [init :as es-init]]
   ;; manual trapperkeeper stuff
   [puppetlabs.trapperkeeper.app :as app]
   [puppetlabs.trapperkeeper.core :as tk]))

(defn log-properties []
  (log/debug (with-out-str
               (do (newline)
                   (utils/safe-pprint
                    (mp/debug-properties-by-source p/PropertiesSchema
                                                   p/files)))))

  (log/info (with-out-str
              (do (newline)
                  (utils/safe-pprint (p/get-global-properties))))))

(defn ^:private services+config []
  (let [;; this is interesting because we can't potentially
        ;; use `get-in-config` here because TK hasn't booted yet.
        ;; might be more appropriate to read from disk here.
        ;; using the properties atom directly as a reminder of this corner case.
        properties @(p/global-properties-atom)
        auth-svc
        (let [{auth-service-type :type :as auth} (get-in properties [:ctia :auth])]
          (case auth-service-type
            :allow-all allow-all/allow-all-auth-service
            :threatgrid threatgrid/threatgrid-auth-service
            :static static-auth/static-auth-service
            (throw (ex-info "Auth service not configured"
                            {:message "Unknown service"
                             :requested-service auth-service-type}))))
        encryption-svc
        (let [{:keys [type] :as encryption-properties}
              (get-in properties [:ctia :encryption])]
          (case type
            :default encryption-default/default-encryption-service
            (throw (ex-info "Encryption service not configured"
                            {:message "Unknown service"
                             :requested-service type}))))]
    {:services (concat
                 [auth-svc
                  encryption-svc
                  e/events-service
                  store-svc/store-service
                  es-svc/es-store-service
                  http-server-svc/ctia-http-server-service
                  hooks-svc/hooks-service
                  graphql-svc/graphql-service]
                 ;; register event file logging only when enabled
                 (when (get-in properties [:ctia :events :log])
                   [event-logging/event-logging-service]))
     :config properties}))

(defn start-ctia!
  "Does the heavy lifting for ctia.main (ie entry point that isn't a class).
  Returns the Trapperkeeper app."
  []
  (log/info "starting CTIA version: "
            (version/current-version))

  ;; properties init
  (p/init!)

  (log-properties)
  (validate-entities)

  ;; metrics reporters init
  ;(riemann/init!)
  ;(jmx/init!)
  ;(console/init!)

  ;; trapperkeeper init
  (let [{:keys [services config]} (services+config)
        app (tk/boot-services-with-config services config)]
    app))
