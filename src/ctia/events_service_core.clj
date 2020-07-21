(ns ctia.events-service-core
  (:require [ctia.lib.async :as la]
            [clj-momo.lib.time :as time]
            [ctia.entity.event.schemas :as es]
            [schema.core :as s]
            [clojure.core.async :as a]))

(defn init [context]
  (assoc context
         :central-channel (la/new-channel)))

(def shutdown-max-wait-ms (* 1000 60 60))

(defn stop 
  "Close the central-channel, waiting up to shutdown-max-wait-ms for the buffer
   to flush."
  [{:keys [central-channel] :as context}]
  (la/shutdown-channel shutdown-max-wait-ms central-channel)
  (dissoc context :central-channel))

(defn central-channel [{:keys [central-channel]}]
  central-channel)

(s/defn send-event
  "Send an event to a channel. Use the central channel by default"
  ([{:keys [central-channel :as context]} event]
   (send-event context central-channel event))
  ([context
    {ch :chan :as echan} :- la/ChannelData
    {:keys [owner timestamp http-params] :as event} :- es/Event]
   (assert owner "Events cannot be registered without user info")
   (let [event (if timestamp event (assoc event :timestamp (time/now)))]
     (a/>!! ch event))))

(s/defn register-listener
  "Convenience wrapper for registering a listener on the central event channel.
  Returns a channel."
  ([context
    listen-fn :- (s/=> s/Any es/Event)
    mode :- (s/enum :compute :blocking)]
   (register-listener listen-fn (constantly true) mode))
  ([{:keys [central-channel] :as _context_}
    listen-fn :- (s/=> s/Any es/Event)
    pred :- (s/=> s/Bool es/Event)
    mode :- (s/enum :compute :blocking)]
   (la/register-listener central-channel listen-fn pred mode)))
