(ns ctia.flows.from-java
  (:require [ctia.flows.hook-protocol :refer [Hook]])
  (:import [java.util HashMap]))

(defn from-java-handle
  "Helper to import Java obeying `Hook` java interface."
  [hook stored-entity prev-entity]
  (into {}
        (.handle hook
                 (when (some? stored-entity)
                   (HashMap. stored-entity))
                 (when (some? prev-entity)
                   (HashMap. prev-entity)))))

(defrecord ProxyJ [o]
  Hook
  (init [_]
    (.init o))
  (handle [_ stored-object prev-object]
    (from-java-handle o stored-object prev-object))
  (destroy [_]
    (.destroy o)))
