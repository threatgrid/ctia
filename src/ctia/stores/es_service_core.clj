(ns ctia.stores.es-service-core
  (:require [clojure.string :as str]
            [ctia.properties :as p]
            [ctia.stores.es.init :as es-init]
            [ctia.store-service-core :refer [empty-stores]]))

;; Note: this implementation assumes StoreService is
;; only ever used by the es-store-service.

(defn- get-store-types [store-kw]
  (or (some-> (p/get-in-global-properties [:ctia :store store-kw])
              (str/split #","))
      []))

(defn- build-store [store-kw store-type]
  (case store-type
    "es" (es-init/init-store! store-kw)))

(defn- init-store-service! [stores]
  (reset! stores
          (->> (keys empty-stores)
               (map (fn [store-kw]
                      [store-kw (keep (partial build-store store-kw)
                                      (get-store-types store-kw))]))
               (into {})
               (merge-with into empty-stores))))

(defn start [context stores]
  (init-store-service! stores)
  context)
