(ns ctia.logging-core
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

(defn start [context register-listener-fn]
  (assoc context :control (register-listener-fn #(log/info "event:" %) :blocking)))

(defn stop [{:keys [control] :as context}]
  (a/close! control)
  (dissoc context :control))
