(ns ctia.stores.es-service-core
  (:require [clojure.string :as str]
            [ctia.properties :as p]
            [ctia.stores.es.init :as es-init]
            [ctia.store-service-core :refer [empty-stores]]))

;; Note: this implementation assumes StoreService is
;; only ever used by the es-store-service.

(defn- get-store-types [store-kw get-in-config]
  (or (some-> (get-in-config [:ctia :store store-kw])
              (str/split #","))
      []))

(defn- build-store [store-kw get-in-config store-type]
  (case store-type
    "es" (es-init/init-store! store-kw get-in-config)))

(defn- init-store-service! [stores get-in-config]
  (reset! stores
          (->> (keys empty-stores)
               (map (fn [store-kw]
                      [store-kw (keep (partial build-store store-kw get-in-config)
                                      (get-store-types store-kw get-in-config))]))
               (into {})
               (merge-with into empty-stores))))

(defn start [context stores get-in-config]
  (init-store-service! stores get-in-config)
  context)
