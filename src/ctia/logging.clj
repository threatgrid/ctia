(ns ctia.logging
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as a :refer [go-loop <! chan tap close!]]
            [ctia.lib.async :as la]
            [schema.core :as s])
  (:import [clojure.core.async.impl.protocols Channel]))

(s/defn log-channel :- Channel
  "Logging an event channel indefinitely. Returns the channel for a go loop that never ends."
  [{m :mult :as ev} :- la/ChannelData]
  (let [events (chan)]
    (tap m events)
    (let [ch (go-loop []
               (when-let [ev (<! events)]
                 (log/info "event:" ev)
                 (recur)))]
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. #(do (close! ch))))
      ch)))
