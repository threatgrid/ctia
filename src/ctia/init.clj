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
   ;; manual trapperkeeper stuff
   [puppetlabs.trapperkeeper.app :as app]
   [puppetlabs.trapperkeeper.core :as tk]
   [puppetlabs.trapperkeeper.internal :as internal]))


(defn- get-store-types [store-kw]
  (or (some-> (get-in @p/properties [:ctia :store store-kw])
              (str/split #","))
      []))

(defn- build-store [store-kw store-type]
  (case store-type
    "es" (es-init/init-store! store-kw)))

(defn init-store-service! []
  (reset! store/stores
          (->> (keys store/empty-stores)
               (map (fn [store-kw]
                      [store-kw (keep (partial build-store store-kw)
                                      (get-store-types store-kw))]))
               (into {})
               (merge-with into store/empty-stores))))

(defn log-properties []
  (log/debug (with-out-str
               (do (newline)
                   (utils/safe-pprint
                    (mp/debug-properties-by-source p/PropertiesSchema
                                                   p/files)))))

  (log/info (with-out-str
              (do (newline)
                  (utils/safe-pprint @p/properties)))))

;;------------------------------------------
;; Start manual Trapperkeeper management
;;------------------------------------------

(defn ^:private services+config []
  (let [properties @p/properties
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
    {:services [auth-svc
                encryption-svc]
     :config properties}))

(defonce ^:private global-app (atom nil))

(defn ^:private tk-shutdown! []
  (when-some [app @global-app]
    (let [shutdown-svc (app/get-service app :ShutdownService)]
      (internal/request-shutdown shutdown-svc)
      (tk/run-app app))
    (reset! global-app nil)))

(defn ^:private tk-init! [services config]
  (shutdown/register-hook! :tk tk-shutdown!)
  (reset! global-app (tk/boot-services-with-config services config)))

;;------------------------------------------
;; End manual Trapperkeeper management
;;------------------------------------------

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
  (validate-entities)

  ;; trapperkeeper init
  (let [{:keys [services config]} (services+config)]
    (tk-init! services config))

  ;; events init
  (e/init!)

  ;; metrics reporters init
  (riemann/init!)
  (jmx/init!)
  (console/init!)

  ;; register event file logging only when enabled
  (when (get-in @p/properties [:ctia :events :log])
    (event-logging/init!))

  (init-store-service!)

  ;; hooks init
  (h/init!)

  ;; Start HTTP server
  (let [{http-port :port
         enabled? :enabled} (get-in @p/properties [:ctia :http])]
    (when enabled?
      (log/info (str "Starting HTTP server on port " http-port))
      (http-server/start! :join? join?))))
