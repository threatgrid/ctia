(ns ctia.logging
  (:require [clojure.tools.logging :as log]
            [ctia.events :as e]))

(defn init!
  "Sets up logging of all events"
  []
  (e/register-listener #(log/info "event:" %)))
