(ns ctia.logging
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ctia.events :as e]
            [ctia.shutdown :as shutdown]))

(defn init!
  "Sets up logging of all events"
  []
  (let [control (e/register-listener #(log/info "event:" %) :blocking)]
    (shutdown/register-hook! :logging #(a/close! control))))
