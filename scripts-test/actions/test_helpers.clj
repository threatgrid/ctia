(ns actions.test-helpers
  (:require [actions.actions-helpers :as h]))

(defn mk-state [_init]
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
               :sh (fn [& args]
                     (throw (Exception. (format "No default :sh stub (args: %s)" (pr-str args)))))
               :getenv (fn [k]
                         (get env-map k))
               :set-output (fn [k v]
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

(defn mk-utils+getenv-history [env-map]
  (let [{:keys [state] :as m} (mk-utils env-map)]
    (-> m
        (assoc-in [:utils :getenv]
                  (fn [k]
                    (swap! state update :history conj
                           {:op :getenv
                            :k k})
                    (get env-map k))))))
