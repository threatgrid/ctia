(ns ctia.events
  (:require [clj-momo.lib.time :as time]
            [ctia.lib.async :as la]
            [ctia.shutdown :as shutdown]
            [ctia.entity.event.schemas :as es]
            [ctim.schemas.common :as c]
            [clojure.core.async :as a :refer [go-loop alt! chan tap]]
            [schema.core :as s :refer [=>]]
            [schema-tools.core :as st])
  (:import [clojure.core.async.impl.protocols Channel]
           [java.util Map]))

(def shutdown-max-wait-ms (* 1000 60 60))

(defonce central-channel (atom nil))

(s/defn shutdown! :- s/Num
  "Close the central-channel, waiting up to max-wait-ms for the buffer
   to flush.  Returns the number of items in the buffer after
   shutdown (zero on a successful flush).  Normally this shouldn't be
   called directly since init! registers a shutdown hook."
  ([]
   (shutdown! shutdown-max-wait-ms))
  ([max-wait-ms :- Long]
   (try
     (if @central-channel
       (la/shutdown-channel max-wait-ms @central-channel)
       0)
     (finally
       (reset! central-channel nil)))))

(defn init! []
  (let [channel-data (la/new-channel)]
    (shutdown/register-hook! :events shutdown!)
    (reset! central-channel channel-data)))

(s/defn send-event
  "Send an event to a channel. Use the central channel by default"
  ([event :- es/Event]
   (send-event @central-channel event))
  ([{ch :chan :as echan} :- la/ChannelData
    {:keys [owner timestamp http-params] :as event} :- es/Event]
   (assert owner "Events cannot be registered without user info")
   (let [event (if timestamp event (assoc event :timestamp (time/now)))]
     (a/>!! ch event))))

(s/defn register-listener :- Channel
  "Convenience wrapper for registering a listener on the central event channel."
  ([listen-fn :- (=> s/Any es/Event)
    mode :- (s/enum :compute :blocking)]
   (register-listener listen-fn (constantly true) mode))
  ([listen-fn :- (=> s/Any es/Event)
    pred :- (=> s/Bool es/Event)
    mode :- (s/enum :compute :blocking)]
   (la/register-listener @central-channel listen-fn pred mode)))
