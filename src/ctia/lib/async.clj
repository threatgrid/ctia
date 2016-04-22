(ns ctia.lib.async
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.verdict :as v]
            [clojure.core.async :as a :refer [go-loop alt! chan tap]]
            [schema.core :as s :refer [=>]])
  (:import [clojure.core.async.impl.protocols Channel]
           [clojure.core.async.impl.buffers FixedBuffer]
           [clojure.core.async Mult]))

(def ^:dynamic *event-buffer-size* 1000)

(s/defschema Event {s/Any s/Any})

(s/defschema EventChannel
  "This structure holds a channel, its associated buffer, and a multichan"
  {:chan-buf FixedBuffer
   :chan Channel
   :mult Mult
   :recent Channel})


(s/defn new-event-channel :- EventChannel []
  (let [b (a/buffer *event-buffer-size*)
        c (a/chan b)
        p (a/mult c)
        r (a/chan (a/sliding-buffer *event-buffer-size*))]
    (a/tap p r)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(do (a/close! c)
                                    (a/close! r))))
    {:chan-buf b
     :chan c
     :mult p
     :recent r}))


(s/defn close!
  "Closes a channel that may have already been reset to nil."
  [c :- (s/maybe Channel)]
  (when c (a/close! c)))


(s/defn shutdown-channel :- s/Num
  "Shuts down a provided event channel."
  [max-wait-ms :- Long
   {:keys [chan-buf chan mult]} :- EventChannel]
  (let [ch (a/chan (a/dropping-buffer 1))]
    (a/tap mult ch)
    (a/close! chan)
    (loop [timeout (a/timeout max-wait-ms)]
      (let [[val _] (a/alts!! [ch timeout] :priority true)]
        (if (some? val)
          (recur timeout)
          (count chan-buf))))))


(s/defn register-listener :- Channel
  "Creates a GO loop to direct events to a function.
   Takes an optional predicate to filter which events are sent to the function.
   Can also take a specified event channel, rather than the central one.

   ec - An event channel, created with new-event-channel.
   f - user provided function that will be called when an event arrives on the event channel.
   pred - a predicate to test events. The user function will only be run if this predicate passes.
   shutdown-fn - an optional function to run at system shutdown time. May be nil."
  [{m :mult :as ec} :- EventChannel
   f :- (=> s/Any Event)
   pred :- (=> s/Bool Event)
   shutdown-fn :- (s/maybe (=> s/Any))]
  (let [events (chan)]
    (tap m events)
    (let [end-chan (chan)]
      (go-loop []
        (if-let [event (alt! [events end-chan] ([v] v))]
          (do
            (when (pred event)
              (f event))
            (recur))
          (do
            (close! end-chan)
            (close! events))))
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. #(do (a/close! end-chan)
                                      (when (fn? shutdown-fn)
                                        (shutdown-fn)))))
      end-chan)))
