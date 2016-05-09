(ns ctia.events
  (:require [ctia.events.schemas :as es]
            [ctia.lib.time :as time]
            [ctia.lib.async :as la]
            [ctia.schemas.common :as c]
            [ctia.schemas.verdict :as v]
            [clojure.core.async :as a :refer [go-loop alt! chan tap]]
            [schema.core :as s :refer [=>]]
            [schema-tools.core :as st])
  (:import [clojure.core.async.impl.protocols Channel]
           [java.util Map]))

(def shutdown-max-wait-ms (* 1000 60 60))

(defonce central-channel (atom nil))

(def ModelEvent (st/merge es/ModelEventBase
                          {s/Any s/Any}))

(defn init! []
  (let [c (la/new-channel)]
    (reset! central-channel c)))

(s/defn shutdown! :- s/Num
  "Close the event channel, waiting up to max-wait-ms for the buffer
   to flush.  Returns the number of items in the buffer after
   shutdown (zero on a successful flush).
   Closes the central channel by default."
  ([]
   (shutdown! shutdown-max-wait-ms))
  ([max-wait-ms :- Long]
   (try
     (if @central-channel
       (la/shutdown-channel max-wait-ms @central-channel)
       0)
     (finally (reset! central-channel nil)))))

(s/defn send-event
  "Send an event to a channel. Use the central channel by default"
  ([event :- es/Event]
   (send-event @central-channel event))
  ([{ch :chan :as echan} :- la/ChannelData
    {:keys [owner timestamp http-params] :as event} :- es/Event]
   (assert owner "Events cannot be registered without user info")
   (let [event (if timestamp event (assoc event :timestamp (time/now)))]
     (a/>!! ch event))))

(s/defn recent-events :- [ModelEvent]
  "Returns up to the requested number of the  most recent events.
   Defaults to attempting to get *event-buffer-size* events."
  ([] (recent-events la/*channel-buffer-size*))
  ([n :- Long] (take n (la/drain (:recent @central-channel)))))

(s/defn register-listener :- Channel
  "Convenience wrapper for registering a listener on the central event channel."
  ([f :- (=> s/Any es/Event)]
   (la/register-listener @central-channel f (constantly true) nil))
  ([f :- (=> s/Any es/Event)
    pred :- (=> s/Bool es/Event)]
   (la/register-listener @central-channel f pred nil))
  ([{m :mult :as ec} :- la/ChannelData
    f :- (=> s/Any es/Event)
    pred :- (=> s/Bool es/Event)]
   (la/register-listener @central-channel f pred nil)))
