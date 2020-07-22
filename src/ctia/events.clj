(ns ctia.events
  (:require [clj-momo.lib.time :as time]
            [ctia.lib.async :as la]
            [ctia.entity.event.schemas :as es]
            [ctia.events-service :as events-svc]
            [ctim.schemas.common :as c]
            [clojure.core.async :as a :refer [go-loop alt! chan tap]]
            [schema.core :as s :refer [=>]]
            [schema-tools.core :as st]
            [puppetlabs.trapperkeeper.core :as tk])
  (:import [clojure.core.async.impl.protocols Channel]))

(defn get-central-channel []
  (events-svc/central-channel
    @events-svc/global-events-service))

(s/defn send-event
  "Send an event to a channel. Use the central channel by default"
  ([event :- es/Event]
   (events-svc/send-event @events-svc/global-events-service event))
  ([channel-data :- la/ChannelData
    event :- es/Event]
   (events-svc/send-event @events-svc/global-events-service
                          channel-data
                          event)))

(s/defn register-listener :- Channel
  "Convenience wrapper for registering a listener on the central event channel."
  ([listen-fn :- (=> s/Any es/Event)
    mode :- (s/enum :compute :blocking)]
   (events-svc/register-listener @events-svc/global-events-service
                                 listen-fn
                                 mode))
  ([listen-fn :- (=> s/Any es/Event)
    pred :- (=> s/Bool es/Event)
    mode :- (s/enum :compute :blocking)]
   (events-svc/register-listener @events-svc/global-events-service
                                 listen-fn
                                 pred
                                 mode)))
