(ns ctia.stores.redis.store
  "Central setup for redis."
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ctia.events :as events]
            [ctia.lib.async :as la]
            [ctia.lib.map :as m]
            [ctia.lib.redis :as lr]
            [ctia.properties :refer [properties]]
            [ctia.properties.getters :as pget]
            [ctia.store :refer [IEventsStore] :as store]
            [schema.core :as s])
  (:import [java.io IOException]
           [java.util UUID]))

(defn- event-subscribe
  "Registers a function to be called with events published on the
   subscription-channel.  Returns a channel that can be closed to
   terminate the subscription loop, or nil if no subscription
   occurred (because Redis is disabled)."
  [receive-event-fn subscription-channel]
  (la/register-listener subscription-channel
                        (fn event-fn [[msg-type channel-name event]]
                          (when (= "message" msg-type)
                            (receive-event-fn event)))
                        (constantly true)
                        nil))

(defn- clear-subscription-state [{{chan :chan} :channel
                                  listener :pubsub-listener
                                  subscriptions :subscriptions}]
  (doseq [control-chan (vals subscriptions)]
    (a/close! control-chan))
  (lr/close-listener listener)
  (a/close! chan))

(defrecord EventsStore [redis-channel-name
                        subscription-state
                        host-port]
  IEventsStore
  (publish-event [_ event]
    (lr/publish host-port
                redis-channel-name
                event))

  (subscribe-to-events [_ listener-fn]
    (when (nil? @subscription-state)
      (reset! subscription-state
              (let [{:keys [chan]
                     :as chan-m} (la/new-event-channel)]
                {:channel chan-m
                 :pubsub-listener (lr/subscribe host-port
                                                redis-channel-name
                                                #(a/>!! chan %))
                 :subscriptions {}})))
    (let [subscription-key (UUID/randomUUID)]
      (swap! subscription-state
             (fn [{chan-m :channel
                   :as previous-subscriptions}]
               (assoc-in previous-subscriptions
                         [:subscriptions subscription-key]
                         (event-subscribe listener-fn chan-m))))
      subscription-key))

  (unsubscribe-to-events [_ subscription-key]
    (when-let [control-chan (get-in @subscription-state
                                    [:subscriptions subscription-key])]
      (swap! subscription-state m/dissoc-in [:subscriptions subscription-key])
      (a/close! control-chan))
    (when (empty? (get @subscription-state :subscriptions))
      (swap! subscription-state clear-subscription-state)))

  (unsubscribe-to-events [_]
    (when (some? @subscription-state)
      (swap! subscription-state clear-subscription-state))))

(defn create-events-store []
  (assert (some? events/central-channel)
          "Events central channel was not set")
  (let [host-port (pget/redis-host-port @properties)
        channel-name (get-in @properties [:ctia :store :redis :channel-name])
        events-store (map->EventsStore
                      {:redis-channel-name channel-name
                       :subscription-state (atom nil)
                       :host-port host-port})]
    (la/register-listener @events/central-channel
                          (fn redis-publisher [event]
                            (store/publish-event events-store event))
                          (constantly true)
                          nil)
    events-store))
