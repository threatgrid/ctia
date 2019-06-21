(ns ctia.shutdown
  (:require [clojure.tools.logging :as log]))

(def shutdown-hooks-initial-state
  {:registered? false
   :hooks {}})

(defonce shutdown-hooks (atom shutdown-hooks-initial-state))

(defn register-hook! [k f]
  (swap! shutdown-hooks assoc-in [:hooks k] f))

(defn shutdown-ctia!
  []
  (log/warn (:hooks @shutdown-hooks))
  (doseq [[name hook] (:hooks @shutdown-hooks)]
    (try
      (hook)
      (catch Exception e
        (log/error e (format "Shutdown hook %s failed" name)))))
  (reset! shutdown-hooks shutdown-hooks-initial-state))

(defn shutdown-ctia-and-agents! []
  (log/warn "shutting down CTIA")
  (shutdown-ctia!)
  (log/warn "shutting down agents")
  (shutdown-agents))

(defn init! []
  (let [t (Thread. shutdown-ctia-and-agents!)]
    (when-not (:registered? @shutdown-hooks)
      (.addShutdownHook
       (Runtime/getRuntime) t)
      (swap! shutdown-hooks assoc :registered? true))))
