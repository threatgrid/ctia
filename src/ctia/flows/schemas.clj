(ns ctia.flows.schemas
  (:require [schema.core :as s]))

(defn error?
  [v]
  (contains? v :error))

(s/defschema EntityError
  {:error s/Any
   :id s/Str
   s/Keyword s/Any})

(defn with-error
  [s]
  (s/if error? EntityError s))
