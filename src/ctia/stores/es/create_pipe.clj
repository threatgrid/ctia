(ns ctia.stores.es.create-pipe
  (:require [clojure.core.async :as a]
            [clojure.core.async.impl.protocols :as ap]
            [ctia.lib.async :as la]
            [ctia.lib.es
             [document :as esd]
             [index :as esi]]
            [ctia.lib.pipes :as lp]
            [ctia.shutdown :as shutdown]
            [schema.core :as s]
            [ctia.lib.async :as la]
            [ctia.lib.pipes :as lp]))

(def pipe (atom nil))

(defrecord ESCreatePipe [work-chan worker-max-sleep-ms max-messages-per-job]
  lp/AsyncPipe
  (start-workers [this num-workers]
    (dotimes [_ num-workers]
      (lp/make-worker this)))

  (shutdown! [_]
    (a/close! work-chan))

  (add-job [_ job]
    (a/>!! work-chan job))

  lp/WorkHandler
  (make-worker [this]
    (a/thread
      ;; block for a bit while waiting for a work message
      (when-let [msg (la/<!! work-chan)]
        (let [msgs
              (loop [msgs [msg]]
                (if (>= (count msgs) max-messages-per-job)
                  msgs
                  (if-let [msg (a/poll! work-chan)]
                    (recur (conj msgs msg))
                    msgs)))]
          (lp/do-some-work this msgs)
          (recur)))))

  (do-some-work [_ msgs]
    (let [refresh? (boolean (some :refresh? msgs))
          results (try
                    (esd/bulk-create-doc
                     (:conn (first msgs))
                     (for [{:keys [document-chan index mapping-type]} msgs
                           :let [{:keys [id] :as document} (la/<!! document-chan)]]
                       (assoc document
                              :_id id
                              :_index index
                              :_type mapping-type))
                     refresh?)
                    (catch Throwable t
                      (repeat t)))]

      (doall
       (map (fn [result {:keys [result-chan] :as msg}]
               (la/on-chan result-chan
                           (if (map? result)
                             (dissoc result :_id :_index :_type)
                             result)))
             results
             msgs)))))

(s/defschema ESCreateMap
  {:document-chan (s/protocol ap/Channel)
   :conn esi/ESConn
   :index s/Str
   :mapping-type s/Str
   :refresh? s/Bool})

(s/defn es-create :- (s/protocol ap/Channel)
  [m :- ESCreateMap]
  (let [result-chan (a/chan 1)]
    (lp/add-job @pipe (assoc m :result-chan result-chan))
    result-chan))

(defn init! []
  (reset! pipe
          (doto (map->ESCreatePipe {:work-chan (a/chan 1000)
                                    :worker-max-sleep-ms 250
                                    :max-messages-per-job 50})
            (lp/start-workers 20)))
  (shutdown/register-hook!
   :store.es.create-pipe
   (fn []
     (swap! pipe
            #(some-> % lp/shutdown!)))))
