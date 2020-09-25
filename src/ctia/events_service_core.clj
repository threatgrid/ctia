(ns ctia.events-service-core
  (:require [clj-momo.lib.time :as time]
            [ctia.lib.async :as la]
            [ctia.entity.event.schemas :as es]
            [ctim.schemas.common :as c]
            [clojure.core.async :as a :refer [go-loop alt! chan tap]]
            [schema.core :as s]
            [schema-tools.core :as st])
  (:import [clojure.core.async.impl.protocols Channel]
           [java.util Map]))

(def shutdown-max-wait-ms (* 1000 60 60))

(defn stop
  "Close the central-channel, waiting up to shutdown-max-wait-ms for the buffer
   to flush."
  [{:keys [central-channel] :as context}]
  (some->> central-channel (la/shutdown-channel shutdown-max-wait-ms))
  (dissoc context :central-channel))

(defn init [context]
  (assoc context
         :central-channel (la/new-channel)))

(s/defn send-event
  "Send an event to a channel. Use the central channel by default"
  ([{:keys [central-channel :as context]} event :- es/Event]
   (send-event context central-channel event))
  ([_context_
    {ch :chan :as echan} :- la/ChannelData
    {:keys [owner timestamp http-params] :as event} :- es/Event]
   (assert owner "Events cannot be registered without user info")
   (let [event (if timestamp event (assoc event :timestamp (time/now)))]
     (a/>!! ch event))))

(s/defn register-listener :- Channel
  "Convenience wrapper for registering a listener on the central event channel."
  ([context
    listen-fn :- (s/=> s/Any es/Event)
    mode :- (s/enum :compute :blocking)]
   (register-listener context listen-fn (constantly true) mode))
  ([{:keys [central-channel]}
    listen-fn :- (s/=> s/Any es/Event)
    pred :- (s/=> s/Bool es/Event)
    mode :- (s/enum :compute :blocking)]
   (la/register-listener central-channel listen-fn pred mode)))

(defn get-central-channel [{:keys [central-channel]}]
  central-channel)
