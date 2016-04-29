(ns hook-example.core
  "An example on how to implement hooks for CTIA"
  (require [ctia.flows.hook-protocol :refer [Hook]]))

(defrecord HookExample [name]
  Hook
  (init [_] :default)
  (destroy [_] :default)
  (handle [_ stored-object prev-object]
    (merge stored-object {name (str  "Passed in " name)})))

(def hook-example-1 (HookExample. "HookExample1"))
(def hook-example-2 (HookExample. "HookExample2"))
