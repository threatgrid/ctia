(ns ctia.logging
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as a :refer [go <!]]
            [ctia.events :as e]
            [schema.core :as s])
  (:import [clojure.core.asyc.impl.protocols Channel]))

(s/defn log-channel :- Channel
  "Logging an event channel indefinitely. Returns the channel for a go loop that never ends."
  [{m :mult :as ev} :- e/EventChannel]
  (go (loop [ev (<! m)]
        (log/info "event: " ev)
        (recur (<! m)))))
