(ns ctia.store-service-core
  (:require [clojure.string :as str]
            [ctia.store :refer [empty-stores close]]
            [ctia.store-service.schemas :refer [Store Stores StoresAtom StoreID StoreServiceCtx]]
            [ctia.stores.es.init :as es-init]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defn init :- StoreServiceCtx
  [context :- (st/optional-keys StoreServiceCtx)]
  (assoc context
         :stores-atom (atom empty-stores)))

(s/defn all-stores :- Stores
  [{:keys [stores-atom]} :- StoreServiceCtx]
  @stores-atom)

(s/defn get-store :- Store
  [ctx :- StoreServiceCtx
   store-id :- StoreID]
  (let [stores (-> ctx all-stores store-id)
        _ (when-not (= 1 (count stores))
            (throw (ex-info (format "Expected one store for %s, found %s." store-id (count stores))
                            {})))]
    (first stores)))

(s/defn ^:private get-store-types
  [store-kw :- StoreID
   get-in-config]
  (or (some-> (get-in-config [:ctia :store store-kw])
              (str/split #","))
      []))

(s/defn ^:private build-store
  [store-kw :- StoreID
   get-in-config
   store-type]
  (case store-type
    "es" (es-init/init-store! store-kw {:ConfigService {:get-in-config get-in-config}})))

(s/defn ^:private init-store-service!
  [stores-atom :- StoresAtom
   get-in-config]
  (reset! stores-atom
          (->> (keys empty-stores)
               (map (fn [store-kw]
                      [store-kw (keep (partial build-store store-kw get-in-config)
                                      (get-store-types store-kw get-in-config))]))
               (into {})
               (merge-with into empty-stores))))

(s/defn start :- StoreServiceCtx
  [{:keys [stores-atom] :as context} :- StoreServiceCtx
   get-in-config]
  (init-store-service! stores-atom get-in-config)
  context)

(s/defn stop :- (st/optional-keys StoreServiceCtx)
  [ctx :- StoreServiceCtx]
  (doseq [[_kw [s]] (all-stores ctx)]
    (close s))
  ctx)
