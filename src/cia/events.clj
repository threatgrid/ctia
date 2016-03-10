(ns cia.events
  (:require #_[cia.events.schemas :as es]
            [cia.schemas.common :as c]
            [clj-time.core :as time]
            [clojure.core.async :as a]
            [schema.core :as s])
  (:import [clojure.core.async Mult]
           [clojure.core.async.impl.protocols Channel]
           [clojure.core.async.impl.buffers FixedBuffer]
           [java.util Map]))

(def shutdown-max-wait-ms (* 1000 60 60))
(def event-buffer-size 1000)

(defonce central-channel (atom nil))

(s/defschema EventChannel
  "This structure holds a channel, its associated buffer, and a multichan"
  {:chan-buf FixedBuffer
   :chan Channel
   :mult Mult})

(s/defschema Event
  "Describes an event"
  {:who s/Str
   :request-params Map
   (s/optional-key :when) c/Time})

(def Triple [(s/one s/Str "s") (s/one s/Str "p") (s/one s/Str "o")])

(def Update
  (merge Event
         {:triples [Triple]}))

(s/defn new-event-channel :- EventChannel []
  (let [b (a/buffer 1000)
        c (a/chan b)
        p (a/mult c)]
    {:chan-buf b
     :chan c
     :mult p}))

(defn init! []
  (reset! central-channel (new-event)))

(s/defn shutdown-channel :- Long
  "Shuts down a provided event channel."
  [max-wait-ms :- Long
   {:keys [:chan-buf :chan :mult]} :- EventChannel]
  (let [ch (a/chan (a/dropping-buffer 1))]
    (a/tap mult ch)
    (a/close! chan)
    (loop [timeout (a/timeout max-wait-ms)]
      (let [[val _] (a/alts!! [ch timeout] :priority true)]
        (if (some? val)
          (recur timeout)
          (count chan-buf))))))

(s/defn shutdown! :- Long
  "Close the event channel, waiting up to max-wait-ms for the buffer
   to flush.  Returns the number of items in the buffer after
   shutdown (zero on a successful flush).
   Closes the central channel by default."
  ([]
   (shutdown! shutdown-max-wait-ms))
  ([max-wait-ms :- Long]
   (shutdown-channel max-wait-ms @central-channel)))

(s/defn send-event
  "Send an event to a channel. Use the central channel by default"
  ([event :- Event]
   (send-event @central-channel event))
  ([{ch :event-chan} :- EventChannel
    {:keys [who when request-params] :as event} :- Event]
   (assert who "Events cannot be registered without user info")
   (assert request-params "HTTP request parameters are required on all events")
   (let [event (if when event (assoc event :when (time/now)))]
     (a/>!! ch event))))

(s/defn send-create-event
  "Builds a creation event and sends it to the provided channel. Use the central channel by default."
  ([model-type
    new-model
    http-params :- Map]
   (send-create-event model-type new-model http-params @central-channel))
  ([model-type
    new-model
    http-params :- Map
    {chan :event-chan} :- EventChannel]
   (send-event chan {:type es/CreateEventType
                     :who "not implemented"
                     :when (time/now)
                     :model model-type
                     :id (:id new-model)
                     :what new-model})))

(defn send-created-model
  ([created-model] (send-created-model created-model @central-channel))
  ([chan :- EventChannel
    created-model]
   (send-event chan created-model)))

(defn send-updated-model
  ([updates] (send-updated-model updates @central-channel))
  ([chan :- EventChannel
    {triples :triples :as update} :- Update]
   (send-event chan update)))

(defn send-deleted-model
  ([] (send-deleted-model @central-channel))
  ([chan]
   (send-event chan {})))

(defn send-verdict-change
  ([] (send-verdict-change @central-channel))
  ([chan]
   (send-event chan {})))
