(ns ctia.http.server-service
  (:require [clojure.tools.logging :as log]
            [ctia.http.server-service-core :as core]
            [ctia.properties :as p]
            [ctia.flows.hooks-service :as hooks-svc]
            [ctia.store-service :as store-svc]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defprotocol CTIAHTTPServerPortService
  (set-port [this port] "Note: Consider set-port internal to CTIAHTTPServerService!

                        Called by CTIAHTTPServerService to set the port bound by HTTP server and returns it.
                        Port can only be set once.")
  (get-port [this] "Returns the port bound by HTTP server. Do not call during TK init/start without very careful
                   consideration -- may cause deadlocks!"))


;; If get-port is called synchronously during TK init/start before CTIAHTTPServerService,
;; has called set-port, a deadlock will occur (since waiting for port-promise will block
;; TK's only thread for lifecycle management, so set-port will never by called).
;; 
;; It is tempting, therefore, to implement get-port such that it throws if port-promise
;; is unrealized:
;; 
;;   (defn- get-port-check-realized [{:keys [port-promise]}]
;;     (when (not (realized? port-promise))
;;       (throw (ex-info (str "CTIAHTTPServerPortService's get-port has no port set!\n"
;;                            "Hint: Do not call during TK init/start")
;;                       {})))
;;     @port-promise)
;; 
;; However this is brittle in other subtle ways. If a service has no relationship to CTIAHTTPServerService,
;; it is undefined which one will be started first. eg., port-logger below might start _before_ CTIAHTTPServerService
;; in dev, but _after_ CTIAHTTPServerService in prod, potentially crashing the logger.
;;
;;   (tk/defservice port-logger
;;     [[:CTIAHTTPServerPortService get-port]]
;;     (start [this _]
;;            (future
;;              (loop []
;;                (something-important)
;;                (println (str "Port is: " (get-port)))))
;;            {}))
;;   
;; Ideally, get-port would live in CTIAHTTPServerService -- this might be possible by using TK's jetty9 implementation,
;; since it supports setting routes _after_ the server has started (which solves the mutual recursion between
;; server startup and routes creation), though there's currently no public interface to retrieving the server port.
;;
;; Another possibility is to introduce a convention that any service that needs get-port must depend on _both_
;; CTIAHTTPServerService and CTIAHTTPServerPortService, to ensure the port 
;;
;; Here, we have gone with a 
(defn- get-port-warn-deadlock [{:keys [port-promise]}]
  (let [warned? (when (not (realized? port-promise))
                  (do (log/warn "port-promise not realized, possible deadlock?")
                      true))
        timeout-sentinel (Object.)
        port-fn #(deref port-promise port-deadlock-warning-ms timeout-sentinel)]
    (loop [port (port-fn)]
      (if (identical? timeout-sentinel port)
        (do (log/warn "Deadlock warning!! CTIAHTTPServerPortService get-port is taking too long to return.")
            (recur (port-fn)))
        (do (when warned?
              (log/warn "port-promise was realized, no deadlock present -- false alarm!"))
            port)))))

(tk/defservice ctia-http-server-port-service
  CTIAHTTPServerPortService
  []
  (init [_ _]
        {:port-promise (promise)})
  (set-port [this port]
            (let [{:keys [port-promise]} (service-context this)]
              (when (realized? port-promise)
                (throw (ex-info "set-port should not be called more than once!"
                                {:old-port @port-promise
                                 :new-port port})))
              (deliver port-promise port)
              port))
  (get-port [this]
            (get-port-warn-deadlock (service-context this))))

(defprotocol CTIAHTTPServerService
  "Responsible for setting CTIAHTTPServerPortService's port via set-port
  by the end of Trapperkeeper's startup.")

(tk/defservice ctia-http-server-service
  CTIAHTTPServerService
  [HooksService
   StoreService
   IAuth
   GraphQLService
   GraphQLNamedTypeRegistryService
   IEncryption
   ConfigService
   CTIAHTTPServerPortService]
  (start [_ context]
         (core/start context
                     ((:get-in-config ConfigService) [:ctia :http])
                     ;; this is effectively internal to CTIAHTTPServerService
                     (:set-port CTIAHTTPServerPortService)
                     {:ConfigService (-> ConfigService
                                         (select-keys [:get-config
                                                       :get-in-config]))
                      :CTIAHTTPServerPortService (-> CTIAHTTPServerPortService
                                                     (select-keys [:get-port]))
                      :HooksService (-> HooksService 
                                        (select-keys [:apply-hooks
                                                      :apply-event-hooks])
                                        hooks-svc/lift-hooks-service-fns)
                      :StoreService (-> StoreService 
                                        (select-keys [:read-store
                                                      :write-store])
                                        store-svc/lift-store-service-fns)
                      :IAuth IAuth
                      :GraphQLService GraphQLService
                      :GraphQLNamedTypeRegistryService GraphQLNamedTypeRegistryService
                      :IEncryption IEncryption}))
  (stop [_ context]
        (core/stop context)))
