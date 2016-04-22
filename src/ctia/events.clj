(ns ctia.events
  (:require [ctia.publish :as publish]
            [ctia.events.schemas :as es]
            [ctia.lib.time :as time]
            [ctia.lib.async :as la]
            [ctia.schemas.common :as c]
            [ctia.schemas.verdict :as v]
            [clojure.core.async :as a :refer [go-loop alt! chan tap]]
            [schema.core :as s :refer [=>]])
  (:import [clojure.core.async.impl.protocols Channel]
           [java.util Map]))

(def shutdown-max-wait-ms (* 1000 60 60))

(defonce central-channel (atom nil))

(def ModelEvent (assoc es/ModelEventBase s/Any s/Any))

(defn init! []
  (let [c (la/new-event-channel)]
    (reset! central-channel c)
    (publish/init! c)))

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
  ([event :- la/Event]
   (send-event @central-channel event))
  ([{ch :chan :as echan} :- la/EventChannel
    {:keys [owner timestamp http-params] :as event} :- la/Event]
   (assert owner "Events cannot be registered without user info")
   (let [event (if timestamp event (assoc event :timestamp (time/now)))]
     (a/>!! ch event))))

(s/defn send-create-event
  "Builds a creation event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams  ; maybe { s/Key s/Any }
    model-type :- s/Str
    new-model :- {s/Any s/Any}]
   (send-create-event @central-channel owner http-params model-type new-model))
  ([echan :- la/EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    model-type :- s/Str
    new-model :- {s/Any s/Any}]
   (send-event echan {:type es/CreateEventType
                      :owner owner
                      :timestamp (time/now)
                      :http-params http-params
                      :model-type model-type
                      :id (or (:id new-model) (gensym "event"))
                      :object new-model})))

(s/defn send-updated-model
  "Builds an updated model event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams
    triples :- [es/UpdateTriple]]
   (send-updated-model @central-channel owner http-params triples))
  ([echan :- la/EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    triples :- [es/UpdateTriple]]
   (send-event echan {:type es/UpdateEventType
                      :owner owner
                      :timestamp (time/now)
                      :http-params http-params
                      :fields triples})))

(s/defn send-deleted-model
  "Builds a delete event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str]
   (send-deleted-model @central-channel owner http-params id))
  ([echan :- la/EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str]
   (send-event echan {:type es/DeleteEventType
                      :owner owner
                      :timestamp (time/now)
                      :id id})))

(s/defn send-verdict-change
  "Builds a verdict change event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str
    verdict :- v/Verdict]
   (send-verdict-change @central-channel owner http-params id verdict))
  ([echan :- la/EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str
    verdict :- v/Verdict]
   (send-event echan {:type es/VerdictChangeEventType
                      :owner owner
                      :timestamp (time/now)
                      :judgement_id id
                      :verdict verdict})))


(s/defn recent-events :- [ModelEvent]
  "Returns up to the requested number of the  most recent events.
   Defaults to attempting to get *event-buffer-size* events."
  ([] (recent-events la/*event-buffer-size*))
  ([n :- Long] (take n (la/drain (:recent @central-channel)))))

(s/defn register-listener :- Channel
  "Convenience wrapper for registering a listener on the central event channel."
  ([f :- (=> s/Any la/Event)]
   (la/register-listener @central-channel f (constantly true) nil))
  ([f :- (=> s/Any la/Event)
    pred :- (=> s/Bool la/Event)]
   (la/register-listener @central-channel f pred nil))
  ([{m :mult :as ec} :- la/EventChannel
    f :- (=> s/Any la/Event)
    pred :- (=> s/Bool la/Event)]
   (la/register-listener @central-channel f pred nil)))
