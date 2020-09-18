(ns ctia.graphql.delayed
  (:refer-clojure :exclude [fn])
  (:require [schema.core :as s]))

;; internal, do not use directly
(deftype DelayedGraphQLWrapper [f])

(defmacro fn
  "Same syntax as schema.core/fn, but returns
  an opaque value that returns true for [[delayed-graphql-value?]]
  and can be unwrapped [[unwrap]]."
  {:style/indent 0}
  [& body]
  `(DelayedGraphQLWrapper.
     (s/fn ~@body)))

(defn unwrap
  [^DelayedGraphQLWrapper v]
  (.f v))

(defn delayed-graphql-value?
  "A flat predicate deciding if the argument is delayed."
  [v]
  (instance? DelayedGraphQLWrapper v))

(defn resolved-graphql-value?
  "A flat predicate deciding if the argument is not delayed."
  [v]
  (not (delayed-graphql-value? v)))
