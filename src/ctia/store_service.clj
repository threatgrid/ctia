(ns ctia.store-service
  (:require [schema.core :as s]))

(s/defn store-service-fn->varargs
  "Given a 2-argument write-store or read-store function (eg., from defservice),
  lifts the function to support variable arguments."
  [store-svc-fn :- (s/=> s/Any
                         (s/named s/Any 
                                  'store)
                         (s/named (s/=> s/Any s/Any)
                                  'f))]
  {:pre [store-svc-fn]}
  (fn [store f & args]
    (store-svc-fn store #(apply f % args))))

(defn lift-store-service-fns
  "Given a map of StoreService services (via defservice), lift
  them to support variable arguments."
  [services]
  (cond-> services
    (:read-store services) (update :read-store store-service-fn->varargs)
    (:write-store services) (update :write-store store-service-fn->varargs)))
