(ns ctia.store-service-core
  (:require [clojure.string :as str]
            [ctia.properties :as p]
            [ctia.store :refer [empty-stores]]
            [ctia.stores.es.init :as es-init]))

(defn init [context]
  (assoc context
         :stores-atom (atom empty-stores)))

(defn all-stores [{:keys [stores-atom]}]
  @stores-atom)

(defn write-store [ctx store write-fn]
  {:pre [(keyword? store)]}
  (first (doall (map write-fn (store (all-stores ctx))))))

(defn read-store [ctx store read-fn]
  {:pre [(keyword? store)]}
  (let [stores (all-stores ctx)
        [s :as ss] (get stores store)
        _ (assert (seq ss)
                  (str "No stores in " store ", only: " (-> stores keys sort vec)))
        _ (assert s [store (find store stores) stores read-fn])]
    (read-fn s)))

(defn- get-store-types [store-kw get-in-config]
  (or (some-> (get-in-config [:ctia :store store-kw])
              (str/split #","))
      []))

(defn- build-store [store-kw get-in-config store-type]
  (case store-type
    "es" (es-init/init-store! store-kw {:ConfigService {:get-in-config get-in-config}})))

(defn- init-store-service! [stores-atom get-in-config]
  (reset! stores-atom
          (->> (keys empty-stores)
               (map (fn [store-kw]
                      [store-kw (keep (partial build-store store-kw get-in-config)
                                      (get-store-types store-kw get-in-config))]))
               (into {})
               (merge-with into empty-stores))))

(defn start [{:keys [stores-atom] :as context} get-in-config]
  (init-store-service! stores-atom get-in-config)
  context)
