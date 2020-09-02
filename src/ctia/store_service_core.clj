(ns ctia.store-service-core
  (:require [ctia.store :refer [empty-stores]]))

(defn init [context]
  (assoc context
         :stores (atom empty-stores)))

(defn stores-atom [{:keys [stores]}]
  stores)

(defn deref-stores [{:keys [stores]}]
  @stores)

(defn write-store [ctx store write-fn]
  {:pre [(keyword? store)]}
  (first (doall (map write-fn (store (deref-stores ctx))))))

(defn read-store [ctx store read-fn]
  {:pre [(keyword? store)]}
  (let [stores (deref-stores ctx)
        [s :as ss] (get stores store)
        _ (assert (seq ss)
                  (str "No stores in " store ", only: " (-> stores keys sort vec)))
        _ (assert s [store (find store stores) stores read-fn])]
    (read-fn s)))
