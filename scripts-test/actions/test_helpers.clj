(ns actions.test-helpers
  (:require [actions.actions-helpers :as h]))

(defn mk-state [init]
  (let [state (atom {:history []})
        grab-history (fn []
                       (let [[{:keys [history]}]
                             (swap-vals! state assoc :history [])]
                         history))]
    {:state state
     :grab-history grab-history}))

(defn mk-utils [env-map]
  (let [{:keys [state] :as state-m} (mk-state {})
        utils {:add-env (fn [_ k v]
                          (swap! state update :history conj
                                 {:op :add-env
                                  :k k
                                  :v v})
                          nil)
               :getenv (fn [k]
                         (get env-map k))
               :set-output (fn [_ k v]
                             (swap! state update :history conj
                                    {:op :set-output
                                     :k k
                                     :v v})
                             nil)
               :set-json-output (fn [_ k v]
                                  (swap! state update :history conj
                                         {:op :set-json-output
                                          :k k
                                          :v v})
                                  nil)}]
    (assert (= (set (keys utils))
               (set (keys h/utils))))
    (assoc state-m :utils utils)))
