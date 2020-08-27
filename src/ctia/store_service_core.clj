(ns ctia.store-service-core
  (:require [ctia.store :refer [empty-stores]]))

(defn init [context]
  (assoc context
         :stores (atom empty-stores)))

(defn get-stores [{:keys [stores]}]
  stores)

(defn write-store [{:keys [stores]} store write-fn]
  {:pre [(keyword? store)]}
  (first (doall (map write-fn (store @stores)))))

(defn read-store [{:keys [stores]} store read-fn]
  {:pre [(keyword? store)]}
  (let [stores @stores
        [s :as ss] (get stores store)
        _ (assert (seq ss)
                  (str "No stores in " store ", only: " (-> stores keys sort vec)))
        _ (assert s [store (find store stores) stores read-fn])]
    (read-fn s)))
