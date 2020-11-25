(ns ctia.store-service.core
  (:require [clojure.string :as str]
            [ctia.properties :as p]
            [ctia.store :refer [empty-stores close]]
            [ctia.stores.es.init :as es-init]
            [ctia.store-service.schemas :refer [Context Services StoresAtom]]
            [schema.core :as s]))

(s/defn init [context]
  (assoc context
         :stores-atom (atom empty-stores)))

(s/defn all-stores [{:keys [stores-atom]} :- Context]
  @stores-atom)

(s/defn write-store
  [ctx :- Context
   store
   write-fn]
  {:pre [(keyword? store)]}
  (first (doall (map write-fn (store (all-stores ctx))))))

(s/defn read-store
  [ctx :- Context
   store
   read-fn]
  {:pre [(keyword? store)]}
  (let [stores (all-stores ctx)
        [s :as ss] (get stores store)
        _ (assert (seq ss)
                  (str "No stores in " store ", only: " (-> stores keys sort vec)))
        _ (assert s [store (find store stores) stores read-fn])]
    (read-fn s)))

(s/defn ^:private get-store-types
  [store-kw :- (s/pred simple-keyword?)
   {{:keys [get-in-config]} :ConfigService} :- Services]
  (or (some-> (get-in-config [:ctia :store store-kw])
              (str/split #","))
      []))

(s/defn ^:private build-store
  [store-kw :- (s/pred simple-keyword?)
   store-type :- s/Str
   services :- Services]
  (case store-type
    "es" (es-init/init-store! store-kw services)))

(s/defn ^:private init-store-service!
  [stores-atom :- StoresAtom
   {{:keys [get-in-config]} :ConfigService :as services} :- Services]
  (reset! stores-atom
          (->> (keys empty-stores)
               (map (fn [store-kw]
                      [store-kw (keep #(build-store store-kw % services)
                                      (get-store-types store-kw services))]))
               (into {})
               (merge-with into empty-stores))))

(s/defn start :- Context
  [{:keys [services stores-atom] :as context} :- Context]
  (init-store-service! stores-atom services)
  context)

(s/defn stop
  [ctx :- Context]
  (doseq [[_ [s]] (all-stores ctx)]
    (close s))
  ctx)
