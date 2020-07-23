(ns ctia.stores.es-service-core
  (:require [clojure.string :as str]
            [ctia.properties :as p]
            [ctia.stores.es.init :as es-init]))

(defn- get-store-types [store-kw]
  (set
    (some-> (get-in @(p/get-global-properties) [:ctia :store store-kw])
            (str/split #","))))

(defn start [context stores]
  (swap! stores
         #(into {}
                (map (fn [[store-kw v]]
                       [store-kw
                        (cond-> v
                          ((get-store-types store-kw) "es")
                          (into (es-init/init-store! store-kw)))]))
                %))
  context)
