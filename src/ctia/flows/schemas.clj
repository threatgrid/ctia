(ns ctia.flows.schemas
  (:require [schema.core :as s]
            [schema-tools.core :as st]))

(defn error?
  [v]
  (contains? v :error))

(s/defschema EntityError
  (st/open-schema
   {:error s/Any
    :id s/Str}))

(defn with-error
  [s]
  (s/if error? EntityError s))
