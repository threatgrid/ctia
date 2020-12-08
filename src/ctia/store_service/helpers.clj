(ns ctia.store-service.helpers
  (:require [ctia.store-service.schemas :refer [StoreID]]
            [schema.core :as s]))

(s/defn invoke-varargs
  "To lift read-store or write-store calls to support variable arguments,
  transform calls from:
    (read-store store-id #(f % a1 a2 a3))
  to:
    (invoke-varargs read-store store-id f a1 a2 a3)"
  [store-svc-fn ;:- ReadStoreFn or WriteStoreFn
   store-id :- StoreID
   f
   & args]
  (store-svc-fn store-id #(apply f % args)))
