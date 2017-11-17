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
             [static :as static-auth]
             [threatgrid :as threatgrid]]
            [ctia.version :as version]
            [ctia.flows.hooks :as h]
            [ctia.http.server :as http-server]
            [ctia.shutdown :as shutdown]
            [ctia.stores.es
             [init :as es-init]
             [store :as es-store]]))

(defn init-auth-service! []
  (let [{auth-service-type :type :as auth} (get-in @p/properties [:ctia :auth])]
    (case auth-service-type
      :allow-all (reset! auth/auth-service (allow-all/->AuthService))
      :threatgrid (reset! auth/auth-service (threatgrid/make-auth-service))
      :static (reset! auth/auth-service (static-auth/->AuthService auth))
      (throw (ex-info "Auth service not configured"
                      {:message "Unknown service"
                       :requested-service auth-service-type})))))

(defn- get-store-types [store-kw]
  (or (some-> (get-in @p/properties [:ctia :store store-kw])
              (clojure.string/split #","))
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
