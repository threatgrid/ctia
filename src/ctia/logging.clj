(ns ctia.logging
  (:require [ctia.logging-core]))

(defn init!
  "Sets up logging of all events"
  []
  (let [control (e/register-listener #(log/info "event:" %) :blocking)]
    (shutdown/register-hook! :logging #(a/close! control))))

(tk/defservice event-logging-service
  [[:EventsService register-listener]]
  (start [this context] (core/start context register-listener))
  (stop [this context] (core/stop context)))
