(ns ctia.flows.hooks-pipe
  (:require [clojure.core.async :as a]
            [clojure.core.async.impl.protocols :as ap]
            [ctia.flows.hook-protocol :as fhp]
            [ctia.lib.async :as la]
            [ctia.lib.pipes :as lp]
            [ctia.shutdown :as shutdown]
            [schema.core :as s]))

(def pipe (atom nil))

(defrecord HooksPipe [work-chan worker-max-sleep-ms]
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

  (do-some-work [_ {:keys [result-chan entity-chan hooks prev-entity read-only?]}]
    (try
      (loop [[hook & more-hooks] hooks
             result (la/<!! entity-chan)]
        (if (nil? hook)
          (la/on-chan result-chan result)
          (let [handle-result (fhp/handle hook result prev-entity)]
            (recur more-hooks (if (or read-only?
                                      (nil? handle-result))
                                result
                                handle-result)))))
      (catch Throwable t
        (la/on-chan result-chan t)))))

(s/defschema PipeHooksMap
  {:entity-chan (s/protocol ap/Channel)
   :hooks [(s/protocol fhp/Hook)]
   :prev-entity (s/maybe {s/Keyword s/Any})
   :read-only? (s/maybe s/Bool)})

(s/defn apply-hooks :- (s/protocol ap/Channel)
  "Put a message on the pipe"
  [m :- PipeHooksMap]
  (let [result-chan (a/chan 1)]
    (lp/add-job @pipe (assoc m :result-chan result-chan))
    result-chan))

(defn init-hooks-pipe! []
  (reset! pipe
          (doto (map->HooksPipe {:work-chan (a/chan 1000)
                                 :worker-max-sleep-ms 250})
            (lp/start-workers 20)))
  (shutdown/register-hook!
   :flows.hooks-pipe
   (fn []
     (swap! pipe
            #(some-> % lp/shutdown!)))))
