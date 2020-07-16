(ns ctia.tk
  "Temporary namespace to manage transition from ctia.init to
  Trapperkeeper."
  (:require [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.internal :as internal]
            [ctia.shutdown :as shutdown]))

(defonce global-app (atom nil))

(defn shutdown! []
  (when-some [app @global-app]
    (let [shutdown-svc (app/get-service app :ShutdownService)]
      (internal/request-shutdown shutdown-svc)
      (tk/run-app app))
    (reset! global-app nil)))

(defn init! []
  (shutdown/register-hook! :tk shutdown!)
  (reset! global-app (tk/boot-services-with-config {} [])))
