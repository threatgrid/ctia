(ns ctia.stores.store-pipe
  (:require [clojure.core.async :as a]
            [clojure.core.async.impl.protocols :as ap]
            [ctia.lib.async :as la]
            [ctia.lib.pipes :as lp]
            [ctia.shutdown :as shutdown]
            [schema.core :as s]))

(def pipe (atom nil))

(defrecord StorePipe [work-chan worker-max-sleep-ms]
  lp/AsyncPipe
  (start-workers [this num-workers]
    (lp/do-start-workers this num-workers))

  (shutdown! [_]
    (a/close! work-chan))

  (add-job [_ job]
    (a/>!! work-chan job))

  lp/WorkHandler
  (make-worker [this]
    (a/thread
      (when-let [msg (la/<!! work-chan)]
        (lp/do-some-work this msg)
        (recur))))

  (do-some-work [_ {:keys [input-chan store-fn result-chan]}]
    (la/on-chan result-chan
                (try
                  (store-fn (la/<!! input-chan))
                  (catch Throwable t t)))))

(s/defschema StorePipeMap
  {:input-chan (s/protocol ap/Channel)
   :store-fn (s/pred fn?)})

(s/defn apply-store-fn :- (s/protocol ap/Channel)
  "Put a message on the pipe"
  [m :- StorePipeMap]
  (let [result-chan (a/chan 1)]
    (lp/add-job @pipe (assoc m :result-chan result-chan))
    result-chan))

(defn init! []
  (reset! pipe
          (doto (map->StorePipe {:work-chan (a/chan 1000)
                                 :worker-max-sleep-ms 250})
            (lp/start-workers 20)))
  (shutdown/register-hook!
   :stores.pipes.store-pipe
   (fn []
     (swap! pipe
            #(some-> % lp/shutdown!)))))
