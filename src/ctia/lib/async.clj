(ns ctia.lib.async
  (:require [clj-momo.lib.time :as time]
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


(s/defn new-channel :- ChannelData
  "Create new ChannelData instance."
  []
  (let [b (a/buffer *channel-buffer-size*)
        c (a/chan b)
        p (a/mult c)
        r (a/chan (a/sliding-buffer *channel-buffer-size*))]
    (a/tap p r)
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
  [& {:keys [mode pred listener-fn data-chan control]}]
  (let [loop-fn (case mode :blocking 'thread :compute 'go-loop)
        alt-fn (case mode :blocking 'alt!! :compute 'alt!)]
    `(~loop-fn []
      (if-let [data# (~alt-fn [~data-chan ~control] ([v#] v#))]
        (do
          (when (~pred data#)
            (~listener-fn data#))
          (recur))
        (do (close! ~control) ;; In case the data-channel was unexpectedly closed
            (close! ~data-chan))))))

(s/defn register-listener :- Channel
  "Creates a GO loop to handle data taken from a channel with a given function.
   Takes an optional predicate to filter what data is sent to the function.

   cd - A ChannelData map
   f - user provided function that will be called when an event arrives on the
       event channel.
   pred - a predicate to test events. The user function will only be run if this
          predicate passes.
   mode - can be :compute (for a green thread) or :blocking (for a real thread)

   Returns a control channel that should be closed to terminate the listener."
  [{m :mult} :- ChannelData
   listener-fn :- (=> s/Any AnyMap)
   pred :- (=> s/Bool AnyMap)
   mode :- (s/enum :compute :blocking)]
  (let [data-chan (chan)
        control (chan)]
    (tap m data-chan)
    (case mode
      :blocking
      (listen :mode :blocking
              :pred pred
              :listener-fn listener-fn
              :data-chan data-chan
              :control control)

      :compute
      (listen :mode :compute
              :pred pred
              :listener-fn listener-fn
              :data-chan data-chan
              :control control))
    control))

(s/defn drain :- [s/Any]
  "Extract elements from a channel into a lazy-seq.
   Reading the seq reads from the channel."
  [c :- Channel]
  (if-let [x (a/poll! c)]
    (cons x (lazy-seq (drain c)))))

(s/defn drain-timed :- [s/Any]
  "Attempt to take all items off of a channel until it is closed,
  returning a collection, but throw if it takes too long."
  ([chan :- Channel]
   (drain-timed chan 1000))
  ([chan :- Channel
    max-ms :- s/Int]
   (let [timer (a/timeout max-ms)]
     (loop [results []]
       (let [[v port] (a/alts!! [chan timer] :priority true)]
         (cond
           (not= port chan) (throw (ex-info "Drain timed out"
                                            {:chan chan
                                             :max-ms max-ms}))
           (nil? v) results
           :else (recur (conj results v))))))))

(defn pipe
  "Alternative to a/pipe that uses a/thread."
  ([from to]
   (pipe from to true))
  ([from to close?]
   (a/thread
     (let [v (a/<!! from)]
       (if (nil? v)
         (when close? (a/close! to))
         (when (a/>!! to v)
           (recur)))))
   to))

(defn pipe!
  "Immediate alternative to a/pipe that uses the current thread.
  Since this isn't asynchronous, it may block the current thread!  In
  such a case, if some other thread isn't taking from the 'to channel,
  the current thread will never un-block.  This can be resolved by
  buffering the 'to channel, but use with caution."
  ([from to]
   (pipe! from to true))
  ([from to close?]
   (loop []
     (let [v (a/<!! from)]
       (if (nil? v)
         (when close? (a/close! to))
         (when (a/>!! to v)
           (recur)))))
   to))

(defn onto-chan
  "Alternative to a/onto-chan that returns the channel where items are put"
  [chan collection]
  (doto chan
    (a/onto-chan collection)))

(defn on-chan
  "Put something on a channel, close the channel, and then return the channel"
  ([thing]
   (on-chan (a/chan 1) thing))
  ([chan thing]
   (doto chan
     (a/>!! thing)
     a/close!)))

(defn throwable
  "If x is a throwable, return it, otherwise nil. Useful when making
  channels that take a transducer (this is the exception handler)."
  [x]
  (when (instance? Throwable x)
    x))

(defn <!!
  "Like a/<!! except that it checks if the payload is a Throwable and
  throws it if so.  This is useful when another thread caught an error
  and put it onto a channel rather than allow it's self to die."
  [chan]
  (let [v (a/<!! chan)]
    (if (instance? Throwable v)
      (throw v)
      v)))
