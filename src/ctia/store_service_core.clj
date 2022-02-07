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
   {{:keys [get-in-config]} :ConfigService}]
  (or (some-> (get-in-config [:ctia :store store-kw])
              (str/split #","))
      []))

(s/defn ^:private build-store
  [store-kw :- StoreID
   services
   store-type]
  (case store-type
    "es" (es-init/init-store! services store-kw)))

(s/defn ^:private init-store-service!
  [services
   stores-atom :- StoresAtom]
  (reset! stores-atom
          (->> (keys empty-stores)
               (pmap (fn [store-kw]
                      [store-kw (keep (partial build-store store-kw services)
                                      (get-store-types store-kw services))]))
               (into {})
               (merge-with into empty-stores))))

(s/defn start :- StoreServiceCtx
  [services
   {:keys [stores-atom] :as context} :- StoreServiceCtx]
  (init-store-service! services stores-atom)
  context)

(s/defn stop :- (st/optional-keys StoreServiceCtx)
  [ctx :- StoreServiceCtx]
  (doseq [[kw [s]] (all-stores ctx)]
    (close s))
  ctx)
