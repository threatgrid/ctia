(ns ctia.lib.async
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.verdict :as v]
            [clojure.core.async :as a :refer [go-loop alt! alt!! chan tap thread]]
            [schema.core :as s :refer [=>]])
  (:import [clojure.core.async.impl.protocols Channel]
           [clojure.core.async.impl.buffers FixedBuffer]
           [clojure.core.async Mult]))

(def ^:dynamic *channel-buffer-size* 1000)

(s/defschema AnyMap {s/Any s/Any})

(s/defschema ChannelData
  "This structure holds a channel, its associated buffer, and a multichan"
  {:chan-buf FixedBuffer
   :chan Channel
   :mult Mult
   :recent Channel})


(s/defn new-channel :- ChannelData []
  (let [b (a/buffer *channel-buffer-size*)
        c (a/chan b)
        p (a/mult c)
        r (a/chan (a/sliding-buffer *channel-buffer-size*))]
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
  "Shuts down a channel provided in a ChannelData map."
  [max-wait-ms :- Long
   {:keys [chan-buf chan mult]} :- ChannelData]
  (let [ch (a/chan (a/dropping-buffer 1))]
    (a/tap mult ch)
    (a/close! chan)
    (loop [timeout (a/timeout max-wait-ms)]
      (let [[val _] (a/alts!! [ch timeout] :priority true)]
        (if (some? val)
          (recur timeout)
          (count chan-buf))))))

(defmacro ^:private listen
  "Helper for register-listener below that handles the mode
   selection (real/green thread)"
  [& {:keys [mode pred listener-fn data-chan end-chan]}]
  (let [loop-fn (case mode :blocking 'thread :compute 'go-loop)
        alt-fn (case mode :blocking 'alt!! :compute 'alt!)]
    `(~loop-fn []
      (if-let [data# (~alt-fn [~data-chan ~end-chan] ([v#] v#))]
        (do
          (when (~pred data#)
            (~listener-fn data#))
          (recur))
        (do (close! ~end-chan)
            (close! ~data-chan))))))

(s/defn register-listener :- Channel
  "Creates a GO loop to handle data taken from a channel with a given function.
   Takes an optional predicate to filter what data is sent to the function.

   cd - A ChannelData map
   f - user provided function that will be called when an event arrives on the event channel.
   pred - a predicate to test events. The user function will only be run if this predicate passes.
   shutdown-fn - an optional function to run at system shutdown time. May be nil.
   mode - can be :compute (for a green thread) or :blocking (for a real thread)"
  ([cd :- ChannelData
    listener-fn :- (=> s/Any AnyMap)
    pred :- (=> s/Bool AnyMap)
    shutdown-fn :- (s/maybe (=> s/Any))]
   (register-listener cd listener-fn pred shutdown-fn :compute))
  ([{m :mult} :- ChannelData
    listener-fn :- (=> s/Any AnyMap)
    pred :- (=> s/Bool AnyMap)
    shutdown-fn :- (s/maybe (=> s/Any))
    mode :- (s/enum :compute :blocking)]
   (let [data-chan (chan)
         end-chan (chan)]
     (tap m data-chan)
     (case mode
       :blocking
       (listen :mode :blocking
               :pred pred
               :listener-fn listener-fn
               :data-chan data-chan
               :end-chan end-chan)

       :compute
       (listen :mode :compute
               :pred pred
               :listener-fn listener-fn
               :data-chan data-chan
               :end-chan end-chan))
     (.addShutdownHook (Runtime/getRuntime)
                       (Thread. #(do (a/close! end-chan)
                                     (when (fn? shutdown-fn)
                                       (shutdown-fn)))))
     end-chan)))

(s/defn drain :- [s/Any]
  "Extract elements from a channel into a lazy-seq.
   Reading the seq reads from the channel."
  [c :- Channel]
  (if-let [x (a/poll! c)]
    (cons x (lazy-seq (drain c)))))
