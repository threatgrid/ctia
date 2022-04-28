(ns ctia.logging-core
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

(def logging-prefix "event:")

(defn start [context register-listener-fn log-fn]
  (assoc context :control (register-listener-fn (or log-fn #(log/info logging-prefix %)) :blocking)))

(defn stop [{:keys [control] :as context}]
  (some-> control a/close!)
  (dissoc context :control))
