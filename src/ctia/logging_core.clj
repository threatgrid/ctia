(ns ctia.logging-core
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

(def logging-prefix "event:")

(defn start [context register-listener-fn]
  (assoc context :control (register-listener-fn #(log/info logging-prefix %) :blocking)))

(defn stop [{:keys [control] :as context}]
  (some-> control a/close!)
  (dissoc context :control))
